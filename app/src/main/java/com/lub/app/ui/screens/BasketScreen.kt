package com.lub.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lub.app.App
import com.lub.app.data.BasketItemEntity
import com.lub.app.parser.StackDetector
import com.lub.app.ui.theme.StatusError
import com.lub.app.ui.theme.StatusSuccess
import kotlinx.coroutines.launch

@Composable
fun BasketScreen(
    onNavigateToReview: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val basketItems by App.instance.database.basketDao().getAllBasketItems().collectAsState(initial = emptyList())

    val selectedItems = basketItems.filter { it.isSelected }

    fun convertToProject() {
        if (selectedItems.isEmpty()) {
            Toast.makeText(context, "يرجى تحديد عنصر واحد على الأقل", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            val filesList = selectedItems.map {
                val relPath = it.targetPath ?: "file_${it.id.take(4)}.${if (it.language != "auto") it.language else "txt"}"
                Pair(relPath, it.code)
            }

            val techStack = StackDetector.detect(filesList.map { it.first }, filesList.toMap())
            val projectId = App.instance.projectStore.saveProject(
                name = "مشروع_السلة_${filesList.size}",
                description = "مشروع تم تجميعه من سلة المقتطفات",
                techStack = techStack,
                files = filesList
            )

            // Clear selected items from basket
            for (item in selectedItems) {
                App.instance.database.basketDao().deleteItem(item)
            }

            onNavigateToReview(projectId)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (basketItems.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            scope.launch { App.instance.database.basketDao().clearBasket() }
                        }
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "تفريغ السلة", tint = StatusError)
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "سلة المقتطفات 🧺",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${basketItems.size} كتل ملتقطة (${selectedItems.size} محددة)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Convert to project button
            if (basketItems.isNotEmpty()) {
                Button(
                    onClick = { convertToProject() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تحويل المحددة إلى مشروع (${selectedItems.size})",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (basketItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBasket,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "السلة فارغة حالياً",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "استخدم الفقاعة العائمة أو زر المشاركة أو الحافظة لإضافة كتل",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(basketItems, key = { it.id }) { item ->
                        BasketItemCard(
                            item = item,
                            onToggleSelection = {
                                scope.launch {
                                    App.instance.database.basketDao().updateItem(item.copy(isSelected = !item.isSelected))
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    App.instance.database.basketDao().deleteItem(item)
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BasketItemCard(
    item: BasketItemEntity,
    onToggleSelection: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "حذف",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = item.header,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "المصدر: ${item.source} | اللغة: ${item.language}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Checkbox(
                        checked = item.isSelected,
                        onCheckedChange = { onToggleSelection() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Code Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = item.code.take(280) + if (item.code.length > 280) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
