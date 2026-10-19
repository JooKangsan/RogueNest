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
				.map(card -> new CardDefinition(card.id(), card.name(), card.tier(), card.price(),
						card.effect(), blankToNull(card.description()), blankToNull(card.image()), card.active(),
						card.synergies() == null ? List.of() : List.copyOf(card.synergies())))
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
		if (effect.trigger() == EffectTrigger.PLAYER_CAUGHT
				&& (effect.caughtRoles() == null || effect.caughtRoles().isEmpty())) {
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
		PLAYER_CAUGHT
	}

	// 효과
	public enum EffectAction {
		// amount G 획득
		GAIN_GOLD,
		// 라운드 종료 보상을 amount G로 바꿈
		SET_ROUND_REWARD
	}

	// 발동 시점 / 조건 / 효과. 조건은 필요한 것만 채운다.
	//   caughtRoles: PLAYER_CAUGHT에서 검거된 사람의 직업이 이 중 하나일 때
	//   maxTurn: 이 턴 이내일 때
	public record CardEffect(
			EffectTrigger trigger,
			List<String> caughtRoles,
			Integer maxTurn,
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
			List<String> synergyTags
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
