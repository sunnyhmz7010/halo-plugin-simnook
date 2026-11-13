package run.halo.simnook;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
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
    void respectsTimezoneLeadTimeAndEventBoundaries() {
        var f = new Fixture();
        var schedule = RenewalSettings.from(Map.of());
        assertThat(schedule.today(Clock.fixed(Instant.parse("2026-11-07T16:00:00Z"), ZoneOffset.UTC)))
                .isEqualTo(LocalDate.of(2026, 11, 8));
        var today = LocalDate.of(2026, 11, 8);
        f.card.getSpec().setNextRenewalAt("2026-11-15");
        assertThat(schedule.includes(CardFacts.from(f.card.getSpec(), today), today))
                .isTrue();
        f.card.getSpec().setNextRenewalAt("2026-11-16");
        assertThat(schedule.includes(CardFacts.from(f.card.getSpec(), today), today))
                .isFalse();
        f.channel.getSpec().setEvents(List.of("window"));
        f.card.getSpec().setNextRenewalAt("2026-11-07");
        f.card.getSpec().setNextRenewalEnd("2026-11-09");
        assertThat(ReminderService.matches(f.channel, f.card, today)).isTrue();
        f.card.getSpec().setNextRenewalEnd("2026-11-07");
        assertThat(ReminderService.matches(f.channel, f.card, today)).isFalse();
        f.channel.getSpec().setEvents(List.of("overdue"));
        assertThat(ReminderService.matches(f.channel, f.card, today)).isTrue();
        f.card.getSpec().setCancelled(true);
        assertThat(ReminderService.matches(f.channel, f.card, today)).isFalse();
    }

    @Test
    void retriesOnlyFailedRecipientAndKeepsNumbersMasked() {
        var f = new Fixture();
        when(f.emitter.emit(anyString(), any()))
                .thenReturn(Mono.empty(), Mono.error(new IllegalStateException("down")), Mono.empty());
        f.service.check().block();
        assertThat(f.channel.getSpec().getSubmittedDates()).containsOnlyKeys("admin");
        assertThat(f.channel.getSpec().getLastError()).contains("other");
        f.service.check().block();
        assertThat(f.channel.getSpec().getSubmittedDates()).containsKeys("admin", "other");
        assertThat(f.channel.getSpec().getLastError()).isNull();
        f.service.check().block();
        verify(f.emitter, times(3)).emit(anyString(), any());
        assertThat(ReminderService.summary(List.of(f.card), LocalDate.of(2026, 11, 8), Locale.ENGLISH))
                .doesNotContain("13800123456");
    }

    @Test
    void disabledEmptyAndUnsubscribedChannelsDoNotSend() {
        var f = new Fixture();
        f.channel.getSpec().setEnabled(false);
        f.service.check().block();
        f.channel.getSpec().setEnabled(true);
        f.channel.getSpec().setEvents(List.of("overdue"));
        f.service.check().block();
        f.channel.getSpec().setEvents(List.of("upcoming"));
        when(f.client.list(eq(Subscription.class), any(), isNull())).thenReturn(Flux.empty());
        f.service.check().block();
        assertThat(f.channel.getSpec().getLastError()).contains("admin");
        verifyNoInteractions(f.emitter);
    }

    @Test
    void testDoesNotConsumeDailyScheduleAndReportsFailure() {
        var f = new Fixture();
        when(f.emitter.emit(anyString(), any())).thenReturn(Mono.error(new IllegalStateException("down")));
        var view = f.service.test("channel-a").block();
        assertThat(view.lastTestError()).contains("admin", "other");
        assertThat(view.lastTestAt()).isNotNull();
        assertThat(f.channel.getSpec().getSubmittedDates()).isEmpty();
        assertThat(view.lastSubmittedAt()).isNull();
    }

    @Test
    void rejectsInvalidInputStaleVersionsAndUnauthorizedRecipients() {
        var f = new Fixture();
        assertThatThrownBy(() -> ReminderService.validate(
                        new ReminderService.ChannelInput(null, "", true, List.of(), List.of(), null)))
                .hasMessageContaining("渠道名称");
        var input =
                new ReminderService.ChannelInput("channel-a", "测试", true, List.of("admin"), List.of("upcoming"), 9L);
        assertThatThrownBy(() -> f.service.save(input).block()).hasMessageContaining("409");
        when(f.roles.listPermissions(anySet())).thenReturn(Flux.empty());
        assertThatThrownBy(() -> f.service.save(input).block()).hasMessageContaining("无号卡管理权限");
        verifyNoInteractions(f.notifications);
    }

    @Test
    void editingDoesNotResubscribeAndDeletionRemovesSubscriptions() {
        var f = new Fixture();
        var input = new ReminderService.ChannelInput(
                "channel-a", "编辑", false, List.of("admin", "other"), List.of("overdue"), 1L);
        assertThat(f.service.save(input).block().enabled()).isFalse();
        verifyNoInteractions(f.notifications);
        f.service.delete("channel-a", 1L).block();
        verify(f.notifications, times(2)).unsubscribe(any(), any());
        verify(f.client).delete(f.channel);
    }

    static class Fixture {
        final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        final ReactiveSettingFetcher settings = mock(ReactiveSettingFetcher.class);
        final NotificationCenter notifications = mock(NotificationCenter.class);
        final NotificationReasonEmitter emitter = mock(NotificationReasonEmitter.class);
        final RoleService roles = mock(RoleService.class);
        final ExternalLinkProcessor links = mock(ExternalLinkProcessor.class);
        final ReminderChannel channel = new ReminderChannel();
        final SimCard card = BackupServiceTest.card("a");
        final ReminderService service;

        Fixture() {
            var metadata = new Metadata();
            metadata.setName("channel-a");
            metadata.setVersion(1L);
            channel.setMetadata(metadata);
            var spec = new ReminderChannel.Spec();
            spec.setDisplayName("提醒");
            spec.setRecipients(List.of("admin", "other"));
            spec.setEvents(List.of("upcoming", "window", "overdue"));
            spec.setEnabled(true);
            channel.setSpec(spec);
            card.getSpec().setNextRenewalAt("2026-11-09");
            when(settings.getSettingValues()).thenReturn(Mono.just(Map.of()));
            when(client.list(eq(SimCard.class), any(), any())).thenReturn(Flux.just(card));
            when(client.list(eq(ReminderChannel.class), any(), any()))
                    .thenAnswer(call -> Flux.defer(() -> {
                        Predicate<ReminderChannel> filter = call.getArgument(1);
                        return Flux.just(channel).filter(filter);
                    }));
            when(client.fetch(eq(ReminderChannel.class), anyString())).thenReturn(Mono.just(channel));
            when(client.update(any(ReminderChannel.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
            when(client.delete(any(ReminderChannel.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
            when(notifications.unsubscribe(any(), any())).thenReturn(Mono.empty());
            var user = new User();
            user.setMetadata(metadata);
            user.setSpec(new User.UserSpec());
            when(client.fetch(eq(User.class), anyString())).thenReturn(Mono.just(user));
            when(roles.getRolesByUsername(anyString())).thenReturn(Flux.just("manager"));
            var role = new Role();
            role.setRules(List.of(new Role.PolicyRule.Builder()
                    .apiGroups("*")
                    .resources("*")
                    .verbs("*")
                    .build()));
            when(roles.listPermissions(anySet())).thenReturn(Flux.just(role));
            var subscriptions = new ArrayList<Subscription>();
            for (var recipient : spec.getRecipients()) {
                var sub = new Subscription();
                sub.setMetadata(metadata);
                var nativeSpec = new Subscription.Spec();
                var subscriber = new Subscription.Subscriber();
                subscriber.setName(recipient);
                nativeSpec.setSubscriber(subscriber);
                nativeSpec.setReason(ReminderService.interest("channel-a", recipient));
                sub.setSpec(nativeSpec);
                subscriptions.add(sub);
            }
            when(client.list(eq(Subscription.class), any(), isNull())).thenAnswer(call -> {
                Predicate<Subscription> filter = call.getArgument(1);
                return Flux.fromIterable(subscriptions).filter(filter);
            });
            when(emitter.emit(anyString(), any())).thenReturn(Mono.empty());
            when(links.processLink(anyString())).thenAnswer(call -> call.getArgument(0));
            service = new ReminderService(
                    client,
                    settings,
                    notifications,
                    emitter,
                    roles,
                    links,
                    Clock.fixed(Instant.parse("2026-11-08T01:00:00Z"), ZoneOffset.UTC));
        }
    }
}
