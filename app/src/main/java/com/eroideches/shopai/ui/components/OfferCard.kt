package com.eroideches.shopai.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eroideches.shopai.core.model.Condition
import com.eroideches.shopai.core.model.Offer
import com.eroideches.shopai.core.ranking.UnitPrice
import com.eroideches.shopai.core.rules.SiteRule
import com.eroideches.shopai.ui.theme.LocalExtra
import com.eroideches.shopai.ui.theme.PriceStyle

/**
 * Scheda prodotto: rettangolo stondato, immagine a sinistra con il tag del sito in alto a sinistra,
 * a destra prezzo, spedizione, descrizione del sito, consegna/valutazione e link "Apri".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OfferCard(
    offer: Offer,
    rule: SiteRule?,
    saved: Boolean,
    best: Boolean,
    unitPrice: UnitPrice?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSave: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = LocalExtra.current
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = if (best) extra.highlight else MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(if (best) Modifier.border(1.5.dp, extra.good.copy(alpha = 0.55f), shape) else Modifier)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(10.dp).height(124.dp)) {
            Box(Modifier.size(104.dp, 124.dp)) {
                ProductImage(offer.imageUrl, Modifier.fillMaxSize())
                SiteTag(rule, offer.siteId, Modifier.align(Alignment.TopStart).padding(4.dp))
                if (best) {
                    Text(
                        "Miglior prezzo", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                            .background(extra.good, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(offer.price.format(), style = PriceStyle, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    IconButton(onClick = onToggleSave, modifier = Modifier.size(30.dp)) {
                        Icon(
                            if (saved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (saved) "Salvato" else "Salva",
                            tint = if (saved) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                ShippingLine(offer, rule)
                if ((offer.shipping.cost?.cents ?: 0) > 0 || unitPrice != null) {
                    Text(
                        listOfNotNull(
                            if ((offer.shipping.cost?.cents ?: 0) > 0) "Totale ${offer.total.format()}" else null,
                            unitPrice?.format(),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    offer.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val info = listOfNotNull(
                        offer.delivery?.let(::shortDelivery),
                        when (offer.condition) { Condition.USED -> "Usato"; Condition.REFURBISHED -> "Ricondiz."; else -> null },
                    ).joinToString(" · ")
                    if (offer.rating != null) {
                        Icon(Icons.Filled.Star, null, tint = Color(0xFFF5A623), modifier = Modifier.size(13.dp))
                        Text(String.format(java.util.Locale.ITALY, "%.1f", offer.rating), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        info, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Apri ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .combinedClickable(onClick = onOpen)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ShippingLine(offer: Offer, rule: SiteRule?) {
    val extra = LocalExtra.current
    val (text, color) = when {
        offer.shipping.isFree -> "Spedizione gratuita" to extra.good
        offer.shipping.cost != null -> "+ ${offer.shipping.cost!!.format()} spedizione" to MaterialTheme.colorScheme.onSurface
        offer.shipping.note != null -> "Spedizione ${offer.shipping.note}" to extra.muted
        else -> (rule?.shippingHint ?: "Spedizione: vedi sito") to extra.muted
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun ProductImage(url: String?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

/** "Consegna GRATUITA mer 26 nov" → "Arriva mer 26 nov". */
fun shortDelivery(s: String): String {
    val m = Regex("(oggi|domani|dopodomani|(lun|mar|mer|gio|ven|sab|dom)[a-z]*\\.?\\s?\\d{1,2}\\s[a-z]{3}[a-z]*|\\d{1,2}\\s(gen|feb|mar|apr|mag|giu|lug|ago|set|ott|nov|dic)[a-z]*)", RegexOption.IGNORE_CASE).find(s)
    return if (m != null) "Arriva ${m.value}" else s.take(28)
}
