package com.alphaomegos.annasagenda.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alphaomegos.annasagenda.AppIcons
import com.alphaomegos.annasagenda.FoodCategory
import com.alphaomegos.annasagenda.FoodLibraryItem
import com.alphaomegos.annasagenda.FoodLibraryUserItem
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.StandardFood
import com.alphaomegos.annasagenda.foodLibrarySearch
import com.alphaomegos.annasagenda.foodLibraryShelf
import com.alphaomegos.annasagenda.parseFoodLibrary

/** Where the shipped library lives; see the header of the file for its format. */
private const val FOOD_LIBRARY_ASSET = "food_library.tsv"

/**
 * The shipped library, read once per screen. An unreadable file gives empty
 * shelves rather than a crash: the user's own foods are still there.
 */
@Composable
internal fun rememberStandardFoods(): List<StandardFood> {
    val ctx = LocalContext.current
    return remember(ctx) {
        runCatching {
            ctx.assets.open(FOOD_LIBRARY_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.map { parseFoodLibrary(it).foods }.getOrDefault(emptyList())
    }
}

internal fun foodCategoryLabelRes(category: FoodCategory): Int = when (category) {
    FoodCategory.MEAT -> R.string.food_category_meat
    FoodCategory.FISH -> R.string.food_category_fish
    FoodCategory.DAIRY -> R.string.food_category_dairy
    FoodCategory.VEGETABLES -> R.string.food_category_vegetables
    FoodCategory.GRAIN -> R.string.food_category_grain
    FoodCategory.BREAD -> R.string.food_category_bread
    FoodCategory.DRINKS -> R.string.food_category_drinks
    FoodCategory.ALCOHOL -> R.string.food_category_alcohol
    FoodCategory.DESSERT -> R.string.food_category_dessert
    FoodCategory.FASTFOOD -> R.string.food_category_fastfood
    FoodCategory.OTHER -> R.string.food_category_other
}

/**
 * 6.4: picking a food from the shelves instead of typing it.
 *
 * Shelves first, then a shelf's foods; a search box over all of them. The
 * user's own foods sit on the same shelves, can be added to any of them and
 * removed again; the shipped ones cannot be removed.
 *
 * Which shelf is open, what is typed in the search and in the add fields all
 * survive turning the phone.
 */
@Composable
internal fun FoodLibraryDialog(
    standard: List<StandardFood>,
    user: List<FoodLibraryUserItem>,
    languageTag: String,
    onPick: (FoodLibraryItem) -> Unit,
    onAddUserFood: (FoodCategory, String, Int) -> Unit,
    onRemoveUserFood: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // The shelf by name: an enum constant's name goes into a Bundle as is.
    var shelfName by rememberSaveable { mutableStateOf<String?>(null) }
    val shelf = FoodCategory.entries.firstOrNull { it.name == shelfName }
    var search by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var addName by rememberSaveable { mutableStateOf("") }
    var addKcal by rememberSaveable { mutableStateOf("") }

    val kcalShort = stringResource(R.string.kcal_short)

    @Composable
    fun FoodRow(item: FoodLibraryItem) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(item) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name)
                val portion = if (item.amount != null && item.unit != null) "${item.amount} ${item.unit} · " else ""
                Text("$portion${item.kcal} $kcalShort", style = MaterialTheme.typography.bodySmall)
            }
            val userId = item.key.removePrefix("user:").toLongOrNull()
            if (item.key.startsWith("user:") && userId != null) {
                IconButton(onClick = { onRemoveUserFood(userId) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove))
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (shelf != null && search.isBlank()) {
                    IconButton(onClick = {
                        shelfName = null
                        adding = false
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.food_library_all_shelves))
                    }
                }
                Text(
                    if (shelf != null && search.isBlank()) stringResource(foodCategoryLabelRes(shelf))
                    else stringResource(R.string.food_library_title)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(stringResource(R.string.food_library_search)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                when {
                    search.isNotBlank() -> {
                        val found = remember(search, standard, user, languageTag) {
                            foodLibrarySearch(search, standard, user, languageTag)
                        }
                        if (found.isEmpty()) {
                            Text(stringResource(R.string.food_library_nothing_found))
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                                items(found, key = { it.key }) { FoodRow(it) }
                            }
                        }
                    }

                    shelf == null -> {
                        // Two columns: every shelf on the screen at once (03.10).
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.heightIn(max = 440.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            gridItems(FoodCategory.entries, key = { it.name }) { category ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { shelfName = category.name }
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = AppIcons.foodCategory(category),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(foodCategoryLabelRes(category)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        val foods = remember(shelf, standard, user, languageTag) {
                            foodLibraryShelf(shelf, standard, user, languageTag)
                        }
                        if (foods.isEmpty()) {
                            Text(stringResource(R.string.food_library_empty))
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(foods, key = { it.key }) { FoodRow(it) }
                        }

                        if (!adding) {
                            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.food_library_add))
                            }
                        } else {
                            OutlinedTextField(
                                value = addName,
                                onValueChange = { addName = it },
                                label = { Text(stringResource(R.string.food_library_add_name)) },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = addKcal,
                                onValueChange = { addKcal = it },
                                label = { Text(stringResource(R.string.diet_dish_kcal)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val kcal = addKcal.trim().toIntOrNull()
                            TextButton(
                                onClick = {
                                    if (kcal != null) {
                                        onAddUserFood(shelf, addName, kcal)
                                        addName = ""
                                        addKcal = ""
                                        adding = false
                                    }
                                },
                                enabled = addName.isNotBlank() && kcal != null && kcal >= 0,
                            ) { Text(stringResource(R.string.food_library_add_save)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
