package inu.timetable.service;

import inu.timetable.dto.SubjectFilterCriteria;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class SubjectCacheWarmupService {

    private final SubjectQueryService subjectQueryService;
    private final boolean enabled;
    private final int concurrency;
    private final int pageSize;

    @Autowired
    public SubjectCacheWarmupService(
            SubjectQueryService subjectQueryService,
            @Value("${subject.cache.warm-up.enabled:true}") boolean enabled,
            @Value("${subject.cache.warm-up.concurrency:4}") int concurrency,
            @Value("${subject.cache.warm-up.page-size:20}") int pageSize) {
        this.subjectQueryService = subjectQueryService;
        this.enabled = enabled;
        this.concurrency = Math.max(1, concurrency);
        this.pageSize = Math.max(1, Math.min(pageSize, SubjectFilterCacheService.MAX_CACHEABLE_PAGE_SIZE));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpAfterApplicationReady() {
        warmUp();
    }

    public WarmupResult warmUp() {
        if (!enabled) {
            return WarmupResult.disabled();
        }

        List<WarmupTask> tasks = buildWarmupTasks();
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(concurrency, tasks.size())));
        ExecutorCompletionService<String> completionService = new ExecutorCompletionService<>(executor);
        try {
            for (WarmupTask task : tasks) {
                completionService.submit(task);
            }

            int succeeded = 0;
            int failed = 0;
            for (int i = 0; i < tasks.size(); i++) {
                try {
                    completionService.take().get();
                    succeeded += 1;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failed += tasks.size() - i;
                    break;
                } catch (ExecutionException exception) {
                    failed += 1;
                    log.warn("Subject cache warm-up task failed", exception.getCause());
                }
            }

            log.info("Subject cache warm-up completed: {} succeeded, {} failed", succeeded, failed);
            return new WarmupResult(false, tasks.size(), succeeded, failed);
        } finally {
            executor.shutdownNow();
        }
    }

    private List<WarmupTask> buildWarmupTasks() {
        List<WarmupTask> tasks = new ArrayList<>();
        tasks.add(new WarmupTask("active-count", () -> {
            subjectQueryService.countActiveSubjects();
            return "active-count";
        }));
        tasks.add(new WarmupTask("departments", () -> {
            subjectQueryService.findDistinctDepartments();
            return "departments";
        }));
        tasks.add(new WarmupTask("default-filter", () -> {
            subjectQueryService.filterSubjects(SubjectFilterCriteria.of(
                    null, null, null, null,
                    null, null, null, null, null, null, 0, pageSize));
            return "default-filter";
        }));

        for (Integer grade : loadGrades()) {
            tasks.add(new WarmupTask("grade-filter-" + grade, () -> {
                subjectQueryService.filterSubjects(SubjectFilterCriteria.of(
                        null, null, null, null,
                        null, null, null, grade, null, null, 0, pageSize));
                return "grade-filter-" + grade;
            }));
        }
        return tasks;
    }

    private List<Integer> loadGrades() {
        try {
            return subjectQueryService.findDistinctGrades();
        } catch (RuntimeException exception) {
            log.warn("Subject cache warm-up could not load grades", exception);
            return List.of();
        }
    }

    private record WarmupTask(String name, Callable<String> callable) implements Callable<String> {

        @Override
        public String call() throws Exception {
            return callable.call();
        }
    }

    public record WarmupResult(boolean skipped, int submitted, int succeeded, int failed) {

        static WarmupResult disabled() {
            return new WarmupResult(true, 0, 0, 0);
        }
    }
}
