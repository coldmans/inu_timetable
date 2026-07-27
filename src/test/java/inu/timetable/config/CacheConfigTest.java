package inu.timetable.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import inu.timetable.dto.SubjectDto;
import inu.timetable.dto.SubjectPageCacheValue;
import inu.timetable.enums.ClassMethod;
import inu.timetable.enums.SubjectType;
import inu.timetable.service.SubjectCacheNames;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CacheConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CacheConfig.class)
            .withPropertyValues("subject.cache.expire-after-write=10m");

    @Test
    void usesCaffeineCacheManagerByDefault() {
        contextRunner.run(context -> {
            CacheManager cacheManager = context.getBean(CacheManager.class);

            assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
        });
    }

    @Test
    void buildsResilientRedisCacheManagerWhenSelected() {
        contextRunner
                .withPropertyValues(
                        "subject.cache.provider=redis",
                        "subject.cache.redis.key-prefix=inu:timetable:test")
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    CacheManager cacheManager = context.getBean(CacheManager.class);

                    assertThat(cacheManager).isInstanceOf(ResilientRedisCacheManager.class);
                    assertThat(SubjectCacheNames.ALL)
                            .allSatisfy(name -> assertThat(cacheManager.getCache(name)).isNotNull());
                });
    }

    @Test
    void buildsTwoLevelCacheManagerWithEveryNamedCacheWhenSelected() {
        contextRunner
                .withPropertyValues(
                        "subject.cache.provider=two-level",
                        "subject.cache.redis.key-prefix=inu:timetable:test:two-level")
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    CacheManager cacheManager = context.getBean(CacheManager.class);

                    assertThat(cacheManager).isInstanceOf(TwoLevelCacheManager.class);
                    assertThat(SubjectCacheNames.ALL)
                            .allSatisfy(name -> assertThat(cacheManager.getCache(name)).isNotNull());
                });
    }

    @Test
    void redisSerializerRoundTripsEveryCachedValueShape() {
        contextRunner
                .withPropertyValues("subject.cache.provider=redis")
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    @SuppressWarnings("unchecked")
                    RedisSerializer<Object> serializer =
                            (RedisSerializer<Object>) context.getBean("subjectCacheValueSerializer");
                    SubjectDto subject = SubjectDto.builder()
                            .id(1L)
                            .subjectName("자료구조")
                            .credits(3)
                            .department("컴퓨터공학부")
                            .grade(2)
                            .subjectType(SubjectType.전심)
                            .classMethod(ClassMethod.OFFLINE)
                            .schedules(new ArrayList<>())
                            .timetableAddCount(7L)
                            .build();

                    Object count = serializer.deserialize(serializer.serialize(2894L));
                    Object subjects = serializer.deserialize(serializer.serialize(
                            new ArrayList<>(List.of(subject))));
                    Object page = serializer.deserialize(serializer.serialize(
                            new SubjectPageCacheValue(List.of(subject), 0, 20, 1)));

                    assertThat(count).isEqualTo(2894L);
                    assertThat(subjects).isInstanceOf(ArrayList.class);
                    assertThat((List<?>) subjects)
                            .singleElement()
                            .isInstanceOf(SubjectDto.class);
                    assertThat(page).isInstanceOf(SubjectPageCacheValue.class);
                    assertThat(((SubjectPageCacheValue) page).toPage().getContent())
                            .extracting(SubjectDto::getSubjectName)
                            .containsExactly("자료구조");
                });
    }
}
