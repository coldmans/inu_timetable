package inu.timetable.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AdminAnalyticsRepository {

    private static final String ACTORIZED_EVENTS_CTE = """
            WITH filtered_events AS (
                SELECT *
                FROM analytics_events
                WHERE occurred_at >= :from
                  AND occurred_at < :to
            ),
            session_user_map AS (
                SELECT session_id,
                       MIN(user_id) AS mapped_user_id,
                       COUNT(DISTINCT user_id) AS user_count
                FROM filtered_events
                WHERE session_id IS NOT NULL
                  AND user_id IS NOT NULL
                GROUP BY session_id
            ),
            actorized_events AS (
                SELECT e.*,
                       CASE
                           WHEN e.user_id IS NOT NULL THEN 'u:' || e.user_id::text
                           WHEN m.user_count = 1 THEN 'u:' || m.mapped_user_id::text
                           WHEN e.session_id IS NOT NULL THEN 's:' || e.session_id
                           ELSE NULL
                       END AS actor_key
                FROM filtered_events e
                LEFT JOIN session_user_map m ON m.session_id = e.session_id
            )
            """;

    private static final String SNAPSHOT_SQL = ACTORIZED_EVENTS_CTE + """
            , actor_rollup AS (
                SELECT actor_key,
                       BOOL_OR(event_type = 'SEARCH') AS searched,
                       BOOL_OR(event_type = 'TIMETABLE_ADD') AS timetable_intent,
                       BOOL_OR(event_type = 'WISHLIST_ADD') AS wishlist_intent,
                       BOOL_OR(event_type = 'COMBINATION_GENERATE') AS combination_intent
                FROM actorized_events
                WHERE actor_key IS NOT NULL
                GROUP BY actor_key
            ),
            timetable_adds AS (
                SELECT user_id, COUNT(*) AS row_count
                FROM user_timetables
                WHERE added_at >= :from
                  AND added_at < :to
                GROUP BY user_id
            ),
            wishlist_adds AS (
                SELECT user_id, COUNT(*) AS row_count
                FROM wishlist_items
                WHERE added_at >= :from
                  AND added_at < :to
                GROUP BY user_id
            ),
            persisted_users AS (
                SELECT COALESCE(t.user_id, w.user_id) AS user_id,
                       COALESCE(t.row_count, 0) AS timetable_rows,
                       COALESCE(w.row_count, 0) AS wishlist_rows
                FROM timetable_adds t
                FULL JOIN wishlist_adds w ON w.user_id = t.user_id
            )
            SELECT
                (SELECT COUNT(*) FROM filtered_events) AS total_events,
                (SELECT COUNT(*) FROM actor_rollup) AS tracked_actors,
                (SELECT COUNT(*) FROM actor_rollup
                    WHERE timetable_intent OR wishlist_intent OR combination_intent) AS core_action_actors,
                (SELECT COUNT(*) FROM actor_rollup WHERE actor_key LIKE 'u:%') AS account_actors,
                (SELECT COUNT(*) FROM actor_rollup WHERE actor_key LIKE 's:%') AS anonymous_actors,
                (SELECT COUNT(*) FROM actor_rollup WHERE searched) AS search_actors,
                (SELECT COUNT(*) FROM actor_rollup
                    WHERE searched AND (timetable_intent OR wishlist_intent OR combination_intent)) AS search_and_core_actors,
                (SELECT COUNT(*) FROM actor_rollup WHERE timetable_intent) AS timetable_intent_actors,
                (SELECT COUNT(*) FROM actor_rollup WHERE wishlist_intent) AS wishlist_intent_actors,
                (SELECT COUNT(*) FROM actor_rollup WHERE combination_intent) AS combination_actors,
                (SELECT COUNT(*) FROM persisted_users) AS persisted_action_users,
                (SELECT COUNT(*) FROM persisted_users WHERE timetable_rows > 0) AS timetable_users,
                COALESCE((SELECT SUM(timetable_rows) FROM persisted_users), 0) AS timetable_rows,
                (SELECT COUNT(*) FROM persisted_users WHERE wishlist_rows > 0) AS wishlist_users,
                COALESCE((SELECT SUM(wishlist_rows) FROM persisted_users), 0) AS wishlist_rows,
                (SELECT COUNT(*) FROM persisted_users
                    WHERE timetable_rows > 0 AND wishlist_rows > 0) AS both_persisted_users,
                (SELECT COUNT(*) FROM users
                    WHERE created_at >= :from AND created_at < :to) AS new_users
            """;

    private static final String ACTION_BREAKDOWN_SQL = ACTORIZED_EVENTS_CTE + """
            SELECT event_type,
                   COUNT(*) AS event_count,
                   COUNT(DISTINCT actor_key) AS actor_count
            FROM actorized_events
            GROUP BY event_type
            ORDER BY event_count DESC, event_type
            """;

    private static final String TOP_SEARCHES_SQL = ACTORIZED_EVENTS_CTE + """
            SELECT label,
                   COUNT(*) AS event_count,
                   COUNT(DISTINCT actor_key) AS actor_count
            FROM actorized_events
            WHERE event_type = 'SEARCH'
              AND label IS NOT NULL
              AND label <> ''
            GROUP BY label
            ORDER BY event_count DESC, label
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Snapshot loadSnapshot(LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.queryForObject(
                SNAPSHOT_SQL,
                bounds(from, to),
                (rs, rowNum) -> new Snapshot(
                        rs.getLong("total_events"),
                        rs.getLong("tracked_actors"),
                        rs.getLong("core_action_actors"),
                        rs.getLong("account_actors"),
                        rs.getLong("anonymous_actors"),
                        rs.getLong("search_actors"),
                        rs.getLong("search_and_core_actors"),
                        rs.getLong("timetable_intent_actors"),
                        rs.getLong("wishlist_intent_actors"),
                        rs.getLong("combination_actors"),
                        rs.getLong("persisted_action_users"),
                        rs.getLong("timetable_users"),
                        rs.getLong("timetable_rows"),
                        rs.getLong("wishlist_users"),
                        rs.getLong("wishlist_rows"),
                        rs.getLong("both_persisted_users"),
                        rs.getLong("new_users")));
    }

    public List<TrendRow> loadTrend(
            LocalDateTime from,
            LocalDateTime to,
            TrendBucket bucket) {
        String bucketExpression = bucket == TrendBucket.HOUR
                ? "date_trunc('hour', occurred_at + INTERVAL '9 hours')"
                : "date_trunc('day', occurred_at + INTERVAL '9 hours')";
        String sql = ACTORIZED_EVENTS_CTE + """
                SELECT %s AS bucket_kst,
                       COUNT(*) AS event_count,
                       COUNT(DISTINCT actor_key) AS actor_count
                FROM actorized_events
                GROUP BY bucket_kst
                ORDER BY bucket_kst
                """.formatted(bucketExpression);

        return jdbcTemplate.query(
                sql,
                bounds(from, to),
                (rs, rowNum) -> new TrendRow(
                        timestampToLocalDateTime(rs.getTimestamp("bucket_kst")),
                        rs.getLong("event_count"),
                        rs.getLong("actor_count")));
    }

    public List<ActionRow> loadActionBreakdown(LocalDateTime from, LocalDateTime to) {
        return jdbcTemplate.query(
                ACTION_BREAKDOWN_SQL,
                bounds(from, to),
                (rs, rowNum) -> new ActionRow(
                        rs.getString("event_type"),
                        rs.getLong("event_count"),
                        rs.getLong("actor_count")));
    }

    public List<SearchRow> loadTopSearches(
            LocalDateTime from,
            LocalDateTime to,
            int limit) {
        MapSqlParameterSource parameters = bounds(from, to)
                .addValue("limit", limit);
        return jdbcTemplate.query(
                TOP_SEARCHES_SQL,
                parameters,
                (rs, rowNum) -> new SearchRow(
                        rs.getString("label"),
                        rs.getLong("event_count"),
                        rs.getLong("actor_count")));
    }

    public AccountTotals loadAccountTotals() {
        String sql = """
                SELECT COUNT(*) AS total_accounts,
                       COUNT(*) FILTER (WHERE status = 'ACTIVE') AS active_accounts
                FROM users
                """;
        return jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource(),
                (rs, rowNum) -> new AccountTotals(
                        rs.getLong("total_accounts"),
                        rs.getLong("active_accounts")));
    }

    private MapSqlParameterSource bounds(LocalDateTime from, LocalDateTime to) {
        return new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to);
    }

    private LocalDateTime timestampToLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public enum TrendBucket {
        HOUR,
        DAY
    }

    public record Snapshot(
            long totalEvents,
            long trackedActors,
            long coreActionActors,
            long accountActors,
            long anonymousActors,
            long searchActors,
            long searchAndCoreActors,
            long timetableIntentActors,
            long wishlistIntentActors,
            long combinationActors,
            long persistedActionUsers,
            long timetableUsers,
            long timetableRows,
            long wishlistUsers,
            long wishlistRows,
            long bothPersistedUsers,
            long newUsers
    ) {
    }

    public record TrendRow(
            LocalDateTime bucketKst,
            long eventCount,
            long actorCount
    ) {
    }

    public record ActionRow(
            String eventType,
            long eventCount,
            long actorCount
    ) {
    }

    public record SearchRow(
            String term,
            long eventCount,
            long actorCount
    ) {
    }

    public record AccountTotals(
            long totalAccounts,
            long activeAccounts
    ) {
    }
}
