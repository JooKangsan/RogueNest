package com.jukangsan.demo.meta;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public final class CardCatalog {
	private final List<CardDefinition> cards;
	private final List<SynergyDefinition> synergies;

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
		Set<String> cardNames = data.cards().stream().map(CardData::name).collect(Collectors.toSet());
		for (SynergyDefinition synergy : data.synergies()) {
			if (!cardNames.containsAll(synergy.cards())) {
				throw new IllegalStateException("시너지 '" + synergy.name() + "'에 등록되지 않은 카드가 있습니다.");
			}
		}

		this.synergies = List.copyOf(data.synergies());
		this.cards = data.cards().stream()
				.map(card -> new CardDefinition(card.id(), card.name(), card.tier(), card.price(),
						card.effect(), card.active(),
						this.synergies.stream()
								.filter(synergy -> synergy.cards().contains(card.name()))
								.map(SynergyDefinition::name)
								.toList()))
				.toList();
	}

	public List<CardDefinition> cards() {
		return cards;
	}

	public List<SynergyDefinition> synergies() {
		return synergies;
	}

	public record CardDefinition(
			String id,
			String name,
			String tier,
			int price,
			String effect,
			boolean active,
			List<String> synergyTags
	) {
	}

	public record SynergyDefinition(String id, String name, List<String> cards, String requirement) {
	}

	private record CatalogData(List<CardData> cards, List<SynergyDefinition> synergies) {
	}

	private record CardData(String id, String name, String tier, int price, String effect, boolean active) {
	}
}
