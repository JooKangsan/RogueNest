package com.jukangsan.demo.meta;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public final class CardCatalog {
	private final List<CardDefinition> cards;
	private final List<SynergyDefinition> synergies;
	// 카드별 효과 (서버 판정용). 화면에 보여주는 설명은 CardDefinition.effect 문구를 쓴다.
	private final Map<String, List<CardEffect>> effectsByCardId;

	public CardCatalog(ObjectMapper objectMapper) {
		CatalogData data;
		try (var input = new ClassPathResource("game/cards.json").getInputStream()) {
			data = objectMapper.readValue(input, CatalogData.class);
		} catch (IOException exception) {
			throw new IllegalStateException("게임 카드 데이터(game/cards.json)를 읽지 못했습니다.", exception);
		}

		if (data.cards() == null || data.synergies() == null
				|| data.cards().size() != 48 || data.synergies().size() != 13) {
			throw new IllegalStateException("게임 카드 데이터는 카드 48장과 시너지 13개를 포함해야 합니다.");
		}
		Map<String, CardData> cardsById = data.cards().stream()
				.collect(Collectors.toMap(CardData::id, Function.identity()));
		if (cardsById.size() != data.cards().size()) {
			throw new IllegalStateException("게임 카드 데이터에 중복된 카드 ID가 있습니다.");
		}
		// 시너지 구성 카드는 각 카드 데이터의 synergies를 모아 만든다. 등급마다 참여 여부가 다를 수 있다.
		Map<String, Set<String>> synergyCardNames = new LinkedHashMap<>();
		for (SynergyData synergy : data.synergies()) {
			if (synergyCardNames.put(synergy.name(), new LinkedHashSet<>()) != null) {
				throw new IllegalStateException("게임 카드 데이터에 중복된 시너지 이름이 있습니다: " + synergy.name());
			}
		}
		for (CardData card : data.cards()) {
			for (String synergyName : card.synergies() == null ? List.<String>of() : card.synergies()) {
				Set<String> names = synergyCardNames.get(synergyName);
				if (names == null) {
					throw new IllegalStateException(
							"카드 '" + card.id() + "'에 존재하지 않는 시너지가 있습니다: " + synergyName);
				}
				names.add(card.name());
			}
		}
		for (Map.Entry<String, Set<String>> entry : synergyCardNames.entrySet()) {
			if (entry.getValue().size() < 2) {
				throw new IllegalStateException("시너지 '" + entry.getKey() + "'에 참여하는 카드가 2종 미만입니다.");
			}
		}

		this.synergies = data.synergies().stream()
				.map(synergy -> new SynergyDefinition(synergy.id(), synergy.name(),
						List.copyOf(synergyCardNames.get(synergy.name())), synergy.requirement()))
				.toList();
		Map<String, List<CardEffect>> effects = new LinkedHashMap<>();
		for (CardData card : data.cards()) {
			List<CardEffect> cardEffects = card.effects() == null ? List.of() : List.copyOf(card.effects());
			for (CardEffect effect : cardEffects) {
				validateEffect(card.id(), effect);
			}
			effects.put(card.id(), cardEffects);
		}
		this.effectsByCardId = Map.copyOf(effects);
		this.cards = data.cards().stream()
				.map(card -> {
					CardEffect activation = effects.get(card.id()).stream()
							.filter(effect -> effect.trigger() == EffectTrigger.ACTIVATE)
							.findFirst()
							.orElse(null);
					return new CardDefinition(card.id(), card.name(), card.tier(), card.price(),
							card.effect(), blankToNull(card.description()), blankToNull(card.image()), card.active(),
							card.synergies() == null ? List.of() : List.copyOf(card.synergies()),
							activation == null ? null : activation.label(),
							activation != null && activation.action().targeted());
				})
				.toList();
	}

	public List<CardDefinition> cards() {
		return cards;
	}

	public List<SynergyDefinition> synergies() {
		return synergies;
	}

	public List<CardEffect> effectsOf(String cardId) {
		return cardId == null ? List.of() : effectsByCardId.getOrDefault(cardId, List.of());
	}

	private static void validateEffect(String cardId, CardEffect effect) {
		if (effect.trigger() == null || effect.action() == null || effect.amount() < 0) {
			throw new IllegalStateException("카드 '" + cardId + "'의 효과 정의가 올바르지 않습니다.");
		}
		if (effect.trigger() == EffectTrigger.ACTIVATE && (effect.label() == null || effect.label().isBlank())) {
			throw new IllegalStateException("카드 '" + cardId + "'의 사용 효과에 버튼 이름(label)이 없습니다.");
		}
		boolean needsRoles = effect.trigger() == EffectTrigger.PLAYER_CAUGHT
				|| effect.trigger() == EffectTrigger.SELF_CAUGHT;
		if (needsRoles && (effect.caughtRoles() == null || effect.caughtRoles().isEmpty())) {
			throw new IllegalStateException("카드 '" + cardId + "'의 검거 효과에 caughtRoles가 없습니다.");
		}
	}

	// 발동 시점
	public enum EffectTrigger {
		// 턴 정산 (생존자 1G 지급 시점)
		TURN_END,
		// 라운드 종료 정산
		ROUND_END,
		// 플레이어 검거 (투표, 도적 탈주)
		PLAYER_CAUGHT,
		// 보유자가 투표로 검거될 때
		SELF_CAUGHT,
		// 보유자가 도적에게 강탈당할 때
		ROBBED,
		// 보유자가 살인마에게 살해당할 때
		KILLED,
		// 보유자가 도적으로서 강탈할 때
		ROBBING,
		// 보유자가 직접 사용 (토론 단계에서만, 라운드당 1회)
		ACTIVATE
	}

	// 효과
	public enum EffectAction {
		// amount G 획득
		GAIN_GOLD,
		// 라운드 종료 보상을 amount G로 바꿈
		SET_ROUND_REWARD,
		// 라운드 종료 시, 카드를 가진 뒤 강탈당한 적이 없으면 amount G 획득
		GAIN_GOLD_IF_NOT_ROBBED,
		// 당하는 일(강탈/살해)을 무효로 함
		NULLIFY,
		// 도적의 강탈을 amount 턴간 봉인
		SEAL_ROBBERY,
		// 강탈한 도적의 골드 amount G를 빼앗음
		STEAL_FROM_ROBBER,
		// 강탈 인원 amount명 추가
		EXTRA_ROBBERY_TARGET,
		// 무작위 생존자 1명에게서 amount G를 빼앗음
		STEAL_RANDOM,
		// 살인마로 검거돼도 죽지 않고 시민으로 복귀
		SURVIVE_AS_CITIZEN,
		// (사용) 즉시 라운드 종료 (마녀)
		END_ROUND,
		// (사용) amount 턴간 강탈과 살인마 행동 불가 (장마)
		RAIN,
		// (사용) 턴 강제 종료 후 대상 즉시 검거. 대상이 시민이면 사용자도 퇴장 (레드카드)
		RED_CARD(true),
		// (사용) 남은 턴의 턴 골드를 한 번에 받고, 이후 턴 골드는 받지 않음 (미래인)
		PULL_TURN_GOLD,
		// (사용) 대상에게서 amount G 강탈 (쎈 누나)
		STEAL_TARGET(true),
		// (사용) 대상 즉시 살해. minTurn 턴부터 가능 (늑대인간)
		ASSASSINATE(true);

		private final boolean targeted;

		EffectAction() {
			this(false);
		}

		EffectAction(boolean targeted) {
			this.targeted = targeted;
		}

		// 사용할 때 대상 플레이어를 골라야 하는지
		public boolean targeted() {
			return targeted;
		}
	}

	// 발동 시점 / 조건 / 효과. 조건은 필요한 것만 채운다.
	//   caughtRoles: PLAYER_CAUGHT에서 검거된 사람의 직업이 이 중 하나일 때
	//   maxTurn: 이 턴 이내일 때
	//   maxUses: 카드를 가진 동안 이 횟수까지만 발동
	//   minTurn: 이 턴부터 가능
	//   label: 사용(ACTIVATE) 버튼 이름
	public record CardEffect(
			EffectTrigger trigger,
			List<String> caughtRoles,
			Integer maxTurn,
			Integer minTurn,
			Integer maxUses,
			String label,
			EffectAction action,
			int amount
	) {
	}

	public record CardDefinition(
			String id,
			String name,
			String tier,
			int price,
			String effect,
			// 카드 설명(플레이버 텍스트). 등급마다 다르게 쓸 수 있다.
			String description,
			// 카드 일러스트 파일명. 프론트 assets/cards/ 안의 파일을 가리킨다.
			String image,
			boolean active,
			List<String> synergyTags,
			// 카드 UI 하단 사용 버튼 이름. 사용할 수 없는 카드면 null
			String actionLabel,
			// 사용할 때 대상을 골라야 하는지
			boolean actionTargeted
	) {
	}

	public record SynergyDefinition(String id, String name, List<String> cards, String requirement) {
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private record CatalogData(List<CardData> cards, List<SynergyData> synergies) {
	}

	private record SynergyData(String id, String name, String requirement) {
	}

	private record CardData(String id, String name, String tier, int price, String effect,
			String description, String image, List<String> synergies, boolean active, List<CardEffect> effects) {
	}
}
