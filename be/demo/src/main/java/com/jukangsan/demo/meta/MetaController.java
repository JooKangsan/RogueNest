package com.jukangsan.demo.meta;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/meta")
public class MetaController {
	private final CardCatalog cardCatalog;

	public MetaController(CardCatalog cardCatalog) {
		this.cardCatalog = cardCatalog;
	}

	@GetMapping("/cards")
	@Operation(summary = "카드 및 시너지 목록", description = "48장의 카드와 공개 시너지 조합을 반환합니다. 시너지 효과는 포함하지 않습니다.")
	public CardCatalogResponse getCards() {
		return new CardCatalogResponse(cardCatalog.cards(), cardCatalog.synergies());
	}

	public record CardCatalogResponse(
			List<CardCatalog.CardDefinition> cards,
			List<CardCatalog.SynergyDefinition> synergies
	) {
	}
}
