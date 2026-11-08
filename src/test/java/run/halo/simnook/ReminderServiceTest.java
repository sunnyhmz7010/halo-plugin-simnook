package run.halo.simnook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Role;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.notification.Subscription;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.ExternalLinkProcessor;
import run.halo.app.notification.NotificationCenter;
import run.halo.app.notification.NotificationReasonEmitter;
import run.halo.app.plugin.ReactiveSettingFetcher;

class ReminderServiceTest {
    @Test
    void scheduleUsesBusinessTimezoneAndSharedUrgencyBoundaries() {
        var settings = RenewalSettings.from(Map.of());
        var clock = Clock.fixed(Instant.parse("2026-11-07T16:00:00Z"), ZoneOffset.UTC);
        assertThat(settings.today(clock)).isEqualTo(LocalDate.of(2026, 11, 8));
        var today = settings.today(clock);
        var card = BackupServiceTest.card("a");
        card.getSpec().setNextRenewalAt(today.plusDays(7).toString());
        var facts = CardFacts.from(card.getSpec(), today);
        assertThat(settings.includes(facts, today)).isTrue();
        assertThat(CardFacts.urgencyCode(facts.status(), facts.renewal(), facts.renewalEnd(), today))
                .isEqualTo("days7");
        card.getSpec().setNextRenewalAt(today.plusDays(8).toString());
        assertThat(settings.includes(CardFacts.from(card.getSpec(), today), today))
                .isFalse();
        card.getSpec().setCancelled(true);
        assertThat(settings.includes(CardFacts.from(card.getSpec(), today), today))
                .isFalse();
        assertThat(settings.includes(
                        CardFacts.from(BackupServiceTest.card("empty").getSpec(), today), today))
                .isFalse();
        var json = BackupService.JSON.readTree("{\"timezone\":\"invalid/timezone\"}");
        assertThatThrownBy(() -> RenewalSettings.from(Map.of("reminders", json)))
                .isInstanceOf(java.time.DateTimeException.class);
    }

    @Test
    void persistsDailySubmissionAndRetriesFailedSubmissionWithoutLeakingNumbers() {
        var fixture = new Fixture();
        fixture.service.check().block(Duration.ofSeconds(3));
        assertThat(fixture.state.getSpec().getLastSubmittedDate()).isEqualTo("2026-11-08");
        fixture.service.check().block(Duration.ofSeconds(3));
        verify(fixture.emitter, times(1)).emit(eq(ReminderService.REASON), any());
        fixture.state.getSpec().setLastSubmittedDate(null);
        when(fixture.emitter.emit(anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("queue down")), Mono.empty());
        fixture.service.check().block(Duration.ofSeconds(3));
        assertThat(fixture.state.getSpec().getLastError()).contains("提交失败");
        fixture.service.check().block(Duration.ofSeconds(3));
        assertThat(fixture.state.getSpec().getLastError()).isNull();
        assertThat(ReminderService.summary(List.of(fixture.card), LocalDate.of(2026, 11, 8), java.util.Locale.ENGLISH))
                .doesNotContain("13800123456");
    }

    @Test
    void respectsUnsubscribeRevokedPermissionAndSendTime() {
        var fixture = new Fixture();
        fixture.state.getSpec().setEnabled(false);
        fixture.service.check().block(Duration.ofSeconds(3));
        fixture.state.getSpec().setEnabled(true);
        when(fixture.client.list(eq(Subscription.class), any(), isNull())).thenReturn(Flux.empty());
        fixture.service.check().block(Duration.ofSeconds(3));
        when(fixture.roles.listPermissions(anySet())).thenReturn(Flux.empty());
        fixture.service.check().block(Duration.ofSeconds(3));
        fixture.service(Clock.fixed(Instant.parse("2026-11-08T00:59:00Z"), ZoneOffset.UTC))
                .check()
                .block(Duration.ofSeconds(3));
        verifyNoInteractions(fixture.emitter);
    }

    static class Fixture {
        final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        final ReactiveSettingFetcher settings = mock(ReactiveSettingFetcher.class);
        final NotificationCenter notifications = mock(NotificationCenter.class);
        final NotificationReasonEmitter emitter = mock(NotificationReasonEmitter.class);
        final RoleService roles = mock(RoleService.class);
        final ExternalLinkProcessor links = mock(ExternalLinkProcessor.class);
        final RenewalReminder state = new RenewalReminder();
        final SimCard card = BackupServiceTest.card("a");
        final ReminderService service;

        Fixture() {
            var metadata = new Metadata();
            metadata.setName(ReminderService.stateName("admin"));
            metadata.setVersion(1L);
            state.setMetadata(metadata);
            var spec = new RenewalReminder.Spec();
            spec.setUsername("admin");
            spec.setEnabled(true);
            state.setSpec(spec);
            card.getSpec().setNextRenewalAt("2026-11-09");
            when(settings.getSettingValues()).thenReturn(Mono.just(Map.of()));
            when(client.list(eq(SimCard.class), any(), any())).thenReturn(Flux.just(card));
            when(client.list(eq(RenewalReminder.class), any(), any()))
                    .thenAnswer(call -> Flux.defer(() -> {
                        Predicate<RenewalReminder> predicate = call.getArgument(1);
                        return Flux.just(state).filter(predicate);
                    }));
            when(client.fetch(eq(RenewalReminder.class), anyString())).thenAnswer(call -> Mono.just(state));
            when(client.update(any(RenewalReminder.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
            var user = new User();
            user.setMetadata(metadata);
            when(client.fetch(User.class, "admin")).thenReturn(Mono.just(user));
            when(roles.getRolesByUsername("admin")).thenReturn(Flux.just("manager"));
            var role = new Role();
            role.setRules(List.of(new Role.PolicyRule.Builder()
                    .apiGroups("*")
                    .resources("*")
                    .verbs("*")
                    .build()));
            when(roles.listPermissions(anySet())).thenReturn(Flux.just(role));
            var subscription = new Subscription();
            subscription.setMetadata(metadata);
            var nativeSpec = new Subscription.Spec();
            var subscriber = new Subscription.Subscriber();
            subscriber.setName("admin");
            nativeSpec.setSubscriber(subscriber);
            var interest = new Subscription.InterestReason();
            interest.setReasonType(ReminderService.REASON);
            interest.setExpression("props.recipient == 'admin'");
            nativeSpec.setReason(interest);
            subscription.setSpec(nativeSpec);
            when(client.list(eq(Subscription.class), any(), isNull())).thenReturn(Flux.just(subscription));
            when(emitter.emit(anyString(), any())).thenReturn(Mono.empty());
            when(links.processLink(anyString())).thenAnswer(call -> call.getArgument(0));
            service = service(Clock.fixed(Instant.parse("2026-11-08T01:00:00Z"), ZoneOffset.UTC));
        }

        ReminderService service(Clock clock) {
            return new ReminderService(client, settings, notifications, emitter, roles, links, clock);
        }
    }
}
