package com.printfinishing.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    MaterialTheme {
        val navController = rememberSwipeDismissableNavController()
        val viewModel: SpecViewModel = viewModel()

        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "list"
        ) {
            composable("list") {
                ListScreen(
                    specs = viewModel.specs,
                    onAdd = { navController.navigate("add") },
                    onOpen = { id -> navController.navigate("detail/$id") }
                )
            }
            composable("add") {
                EditScreen(
                    initial = null,
                    onSave = { spec -> viewModel.add(spec) }
                )
            }
            composable("detail/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")
                val spec = viewModel.get(id)
                if (spec != null) {
                    DetailScreen(
                        spec = spec,
                        onEdit = { navController.navigate("edit/${spec.id}") },
                        onDelete = {
                            viewModel.delete(spec.id)
                            navController.popBackStack()
                        }
                    )
                }
            }
            composable("edit/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")
                val spec = viewModel.get(id)
                if (spec != null) {
                    EditScreen(
                        initial = spec,
                        onSave = { updated ->
                            viewModel.update(updated)
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
