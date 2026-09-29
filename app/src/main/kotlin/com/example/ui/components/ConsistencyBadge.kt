package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FundConsistency
import com.example.domain.ranking.ReturnHeatmap
import com.example.ui.util.Formatters

/**
 * نشان «تداوم بازدهی» روی کارت هر صندوق.
 *
 * رنگش **تک‌سویه** است، نه سبز/قرمز: امتیاز پایین یعنی «در دسته‌اش عقب است»،
 * نه «زیان داده». رنگ‌آمیزی دوسویه این دو را یکی نشان می‌داد.
 *
 * عدد و برچسبِ متنی هر دو نوشته می‌شوند، پس نشان بدون تشخیص رنگ هم خواناست.
 *
 * وقتی امتیاز `null` است نشان حذف نمی‌شود بلکه «—» می‌گیرد: نبودِ داده خودش
 * یک خبر است (یعنی سابقه صندوق زیر ۳۰ روز است)، و حذف بی‌صدای نشان،
 * کارت‌ها را ناهمسان می‌کرد.
 */
@Composable
fun ConsistencyBadge(
    consistency: FundConsistency?,
    modifier: Modifier = Modifier
) {
    val alpha = ReturnHeatmap.sequentialAlpha(consistency?.displayScore)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "تداوم بازدهی",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = consistency?.let { Formatters.toPersianDigits(it.displayScore.toString()) } ?: "—",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (consistency != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = buildString {
                    append("· ")
                    append(consistency.label)
                    consistency.rankLabel?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
