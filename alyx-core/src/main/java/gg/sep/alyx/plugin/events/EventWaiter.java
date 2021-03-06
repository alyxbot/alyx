package gg.sep.alyx.plugin.events;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

/**
 * The {@link EventWaiter} class provides an asynchronous method
 * to wait for certain Discord events to fire which meet a certain condition.
 */
@Log4j2
public class EventWaiter implements EventListener {

    private static final long THREAD_CHECK_INTERVAL = 50;

    private final ExecutorService executorService;

    private final Map<Class<? extends GenericEvent>, Collection<EventTask>> runningTasks =
        new ConcurrentHashMap<>();

    /**
     * Constructs a new instances of an Event Waiter.
     * @param identifier Identifier used to name the threads spawned by this Event Waiter. A good
     *                  practices is to use the Bot's name.
     */
    public EventWaiter(final String identifier) {

        final ThreadFactory threadFactory = new BasicThreadFactory.Builder()
            .namingPattern("EventWaiter-" + identifier + "-%d")
            .daemon(true)
            .build();

        // TODO: Revisit this since this may be a bottleneck for large bot instances
        this.executorService = Executors.newFixedThreadPool(8, threadFactory);
    }

    @RequiredArgsConstructor
    private static final class EventTask<T, R> {
        private final Predicate<T> condition;
        private final Function<T, R> completedAction;
        private volatile boolean completed;
        private volatile R result = null;

        private synchronized boolean check(final T event) {
            final boolean check = condition.test(event);
            if (check) {
                completed = true;
                result = completedAction.apply(event);
                return true;
            }
            return false;
        }

        private synchronized boolean isCompleted() {
            return completed;
        }

        private synchronized R getResult() {
            return result;
        }
    }

    /**
     * Wait for a Discord event of type {@code T} to fire which matches the given {@code predicate}.
     *
     * If the predicate is successful, {@code callback} is triggered in order to return the result.
     *
     * If the timeout was reached without the event being fired, the contents of the result
     * will be {@code null}. Your calling function will need to handle this case appropriately.
     *
     * @param clazz The class of the Discord event, extending from {@link GenericEvent}.
     * @param condition The condition to check against the event in order to determine if
     *                  the event completed.
     * @param completedCallback Callback to execute on the event once the wait is completed.
     * @param timeout Maximum duration to wait for the event to be completed.
     * @param <T> The type of the Discord event.
     * @param <R> The type of result that is returned once the {@code callback} is applied to the event.
     * @return Asynchronous future containing the result of the {@code callback} applied to the event.
     */
    public <T extends GenericEvent, R> CompletableFuture<R> waitForEvent(final Class<T> clazz,
                                                                         final Predicate<T> condition,
                                                                         final Function<T, R> completedCallback,
                                                                         final Duration timeout) {

        final EventTask<T, R> eventTask = new EventTask<>(condition, completedCallback);
        final Collection<EventTask> currentTasks = runningTasks.computeIfAbsent(clazz,
            key -> ConcurrentHashMap.newKeySet());
        currentTasks.add(eventTask);

        final Supplier<R> supplier = () -> {
            final long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < timeout.toMillis()) {
                if (eventTask.isCompleted()) {
                    break;
                }
                try {
                    Thread.sleep(THREAD_CHECK_INTERVAL);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

            }
            currentTasks.remove(eventTask);
            return eventTask.getResult();
        };

        return CompletableFuture.supplyAsync(supplier, executorService);
    }

    /**
     * Triggers on all Discord events that the bot receives in order to determine if
     * the event matches any events waiting.
     *
     * @param event Discord event.
     */
    @Override
    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void onEvent(final GenericEvent event) {
        final Collection<EventTask> tasks = runningTasks.get(event.getClass());

        if (tasks != null) {
            final Collection<EventTask> finishedTasks = tasks.stream()
                .filter(task -> task.check((event)))
                .collect(Collectors.toList());
            tasks.removeAll(finishedTasks);
        }
    }

    /**
     * Tells the EventWaiter to reject new tasks and wait for all running tasks to finish, up to
     * the timeout specified in {@code timeout}/{@code unit}.
     *
     * @param timeout The maximum time to wait
     * @param unit The time unit of the timeout argument
     * @return {@code true} if this executor terminated successfully and
     *         {@code false} if the timeout elapsed before termination
     */
    public CompletableFuture<Boolean> shutdown(final long timeout, final TimeUnit unit) {
        final AtomicBoolean result = new AtomicBoolean(false);
        return CompletableFuture.runAsync(() -> {
            this.executorService.shutdown();
            try {
                result.set(this.executorService.awaitTermination(timeout, unit));
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                result.set(false);
            }
        }).thenApply(f -> result.get());
    }
}
