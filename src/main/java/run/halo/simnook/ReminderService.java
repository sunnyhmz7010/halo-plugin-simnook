package run.halo.simnook;

import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.notification.Reason;
import run.halo.app.core.extension.notification.Subscription;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.ExternalLinkProcessor;
import run.halo.app.notification.NotificationCenter;
import run.halo.app.notification.NotificationReasonEmitter;
import run.halo.app.notification.UserIdentity;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Slf4j
@Component
public class ReminderService {
    static final String REASON = "simnook-renewal-reminder";
    private final ReactiveExtensionClient client;
    private final ReactiveSettingFetcher settings;
    private final NotificationCenter notifications;
    private final NotificationReasonEmitter emitter;
    private final RoleService roles;
    private final ExternalLinkProcessor links;
    private final Clock clock;

    @Autowired
    public ReminderService(
            ReactiveExtensionClient client,
            ReactiveSettingFetcher settings,
            NotificationCenter notifications,
            NotificationReasonEmitter emitter,
            RoleService roles,
            ExternalLinkProcessor links) {
        this(client, settings, notifications, emitter, roles, links, Clock.systemUTC());
    }

    ReminderService(
            ReactiveExtensionClient client,
            ReactiveSettingFetcher settings,
            NotificationCenter notifications,
            NotificationReasonEmitter emitter,
            RoleService roles,
            ExternalLinkProcessor links,
            Clock clock) {
        this.client = client;
        this.settings = settings;
        this.notifications = notifications;
        this.emitter = emitter;
        this.roles = roles;
        this.links = links;
        this.clock = clock;
    }

    @Schema(name = "ReminderSubscriptionUpdate")
    public record SubscriptionUpdate(boolean subscribed) {}

    @Schema(name = "ReminderSubscriptionView")
    public record SubscriptionView(
            boolean subscribed,
            String nextRunAt,
            String lastSubmittedAt,
            String lastError,
            String sendTime,
            String timezone,
            int daysAhead) {}

    Mono<SubscriptionView> view(String username) {
        return settings.getSettingValues()
                .map(RenewalSettings::from)
                .flatMap(schedule -> state(username)
                        .zipWith(hasNativeSubscription(username))
                        .map(pair -> {
                            var spec = pair.getT1().getSpec();
                            var enabled = spec.isEnabled() && pair.getT2();
                            var now = ZonedDateTime.now(clock.withZone(schedule.zone()));
                            var next = now.toLocalDate()
                                    .atTime(schedule.sendTime())
                                    .atZone(schedule.zone());
                            if (now.toLocalDate().toString().equals(spec.getLastSubmittedDate()))
                                next = next.plusDays(1);
                            else if (next.isBefore(now)) next = now;
                            return new SubscriptionView(
                                    enabled,
                                    enabled ? next.toInstant().toString() : null,
                                    spec.getLastSubmittedAt(),
                                    spec.getLastError(),
                                    schedule.sendTime().toString(),
                                    schedule.zone().getId(),
                                    schedule.daysAhead());
                        }));
    }

    Mono<SubscriptionView> subscribe(String username, boolean enabled) {
        return state(username)
                .flatMap(state -> {
                    if (enabled) {
                        return notifications
                                .subscribe(subscriber(username), interest(username))
                                .then(Mono.defer(() -> {
                                    state.getSpec().setEnabled(true);
                                    return persist(state);
                                }));
                    }
                    state.getSpec().setEnabled(false);
                    return persist(state)
                            .flatMap(saved -> notifications
                                    .unsubscribe(subscriber(username), interest(username))
                                    .thenReturn(saved));
                })
                .then(view(username));
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public Mono<Void> check() {
        return Mono.defer(() -> settings.getSettingValues()
                        .map(RenewalSettings::from)
                        .flatMap(schedule -> {
                            var now = ZonedDateTime.now(clock.withZone(schedule.zone()));
                            if (now.toLocalTime().isBefore(schedule.sendTime())) return Mono.empty();
                            var today = now.toLocalDate();
                            return client.list(
                                            SimCard.class,
                                            card -> card.getMetadata().getDeletionTimestamp() == null,
                                            PublicCard.SOURCE_ORDER)
                                    .filter(card -> schedule.includes(CardFacts.from(card.getSpec(), today), today))
                                    .collectList()
                                    .filter(cards -> !cards.isEmpty())
                                    .flatMap(cards -> client.list(
                                                    RenewalReminder.class,
                                                    state -> state.getMetadata().getDeletionTimestamp() == null
                                                            && state.getSpec().isEnabled(),
                                                    Comparator.comparing(state ->
                                                            state.getMetadata().getName()))
                                            .concatMap(state -> deliver(state, cards, today))
                                            .then());
                        }))
                .doOnError(error -> log.error("SIMnook 续费提醒检查失败", error))
                .onErrorComplete();
    }

    Mono<Void> deliver(RenewalReminder state, List<SimCard> cards, LocalDate today) {
        var username = state.getSpec().getUsername();
        if (today.toString().equals(state.getSpec().getLastSubmittedDate())) return Mono.empty();
        return canManage(username)
                .filter(Boolean::booleanValue)
                .flatMap(allowed -> hasNativeSubscription(username))
                .filter(Boolean::booleanValue)
                .flatMap(subscribed -> emitter.emit(
                                REASON,
                                builder -> builder.author(UserIdentity.of("system"))
                                        .subject(Reason.Subject.builder()
                                                .apiVersion("v1alpha1")
                                                .kind("ConfigMap")
                                                .name("simnook-config")
                                                .title("SIMnook")
                                                .url(links.processLink("/console/simnook"))
                                                .build())
                                        .attribute("recipient", username)
                                        .attribute("businessDate", today.toString())
                                        .attribute("summary", summary(cards, today, Locale.SIMPLIFIED_CHINESE))
                                        .attribute("summaryEn", summary(cards, today, Locale.ENGLISH)))
                        .then(Mono.defer(() -> state(username).flatMap(latest -> {
                            latest.getSpec().setLastSubmittedDate(today.toString());
                            latest.getSpec().setLastSubmittedAt(clock.instant().toString());
                            latest.getSpec().setLastError(null);
                            return persist(latest);
                        }))))
                .then()
                .onErrorResume(error -> {
                    log.error("SIMnook 提醒提交失败，用户 {}", username, error);
                    return state(username)
                            .flatMap(latest -> {
                                latest.getSpec().setLastError("提交失败，请检查 Halo 通知设置与服务端日志");
                                return persist(latest);
                            })
                            .then();
                });
    }

    Mono<Boolean> canManage(String username) {
        return client.fetch(User.class, username)
                .filter(user -> user.getMetadata().getDeletionTimestamp() == null
                        && !Boolean.TRUE.equals(user.getSpec().getDisabled()))
                .flatMap(user -> roles.getRolesByUsername(username)
                        .collectList()
                        .flatMapMany(names -> roles.listPermissions(new java.util.HashSet<>(names)))
                        .flatMapIterable(role -> role.getRules() == null ? List.of() : role.getRules())
                        .any(rule -> contains(rule.getApiGroups(), "console.api.simnook.halo.run")
                                && contains(rule.getResources(), "cardviews")
                                && (contains(rule.getVerbs(), "list") || contains(rule.getVerbs(), "get"))
                                && rule.getResourceNames().length == 0))
                .defaultIfEmpty(false);
    }

    private static boolean contains(String[] values, String value) {
        return Arrays.asList(values).contains("*") || Arrays.asList(values).contains(value);
    }

    private Mono<Boolean> hasNativeSubscription(String username) {
        return client.list(
                        Subscription.class,
                        subscription -> subscription.getMetadata().getDeletionTimestamp() == null
                                && subscription.getSpec() != null
                                && subscription.getSpec().getSubscriber() != null
                                && username.equals(
                                        subscription.getSpec().getSubscriber().getName())
                                && subscription.getSpec().getReason() != null
                                && REASON.equals(
                                        subscription.getSpec().getReason().getReasonType())
                                && interest(username)
                                        .getExpression()
                                        .equals(subscription
                                                .getSpec()
                                                .getReason()
                                                .getExpression()),
                        null)
                .hasElements();
    }

    private static Subscription.Subscriber subscriber(String username) {
        var subscriber = new Subscription.Subscriber();
        subscriber.setName(username);
        return subscriber;
    }

    private static Subscription.InterestReason interest(String username) {
        var interest = new Subscription.InterestReason();
        interest.setReasonType(REASON);
        interest.setExpression("props.recipient == '" + username.replace("'", "''") + "'");
        return interest;
    }

    private Mono<RenewalReminder> state(String username) {
        return client.fetch(RenewalReminder.class, stateName(username)).switchIfEmpty(Mono.fromSupplier(() -> {
            var state = new RenewalReminder();
            var metadata = new Metadata();
            metadata.setName(stateName(username));
            state.setMetadata(metadata);
            var spec = new RenewalReminder.Spec();
            spec.setUsername(username);
            state.setSpec(spec);
            return state;
        }));
    }

    private Mono<RenewalReminder> persist(RenewalReminder state) {
        return state.getMetadata().getVersion() == null ? client.create(state) : client.update(state);
    }

    static String stateName(String username) {
        try {
            return "reminder-"
                    + HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256")
                                    .digest(username.getBytes(StandardCharsets.UTF_8)))
                            .substring(0, 40);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String summary(List<SimCard> cards, LocalDate today, Locale locale) {
        return cards.stream()
                .map(card -> {
                    var view = PublicCard.from(card, today);
                    return view.name() + " · " + view.numberMasked() + " | "
                            + CardFacts.urgency(
                                    view.status(), view.nextRenewalAt(), view.nextRenewalEnd(), today, locale)
                            + " | " + view.nextRenewalAt()
                            + (view.nextRenewalEnd() == null ? "" : " ~ " + view.nextRenewalEnd());
                })
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
