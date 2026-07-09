@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val tabs = listOf("home", "saved_list")
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == "home",
                    onClick = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "主页") },
                    label = { Text("主页") }
                )
                NavigationBarItem(
                    selected = currentRoute == "saved_list",
                    onClick = {
                        navController.navigate("saved_list") {
                            popUpTo("saved_list") { inclusive = true }
                        }
                    },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "已保存路线") },
                    label = { Text("已保存") }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                RecordScreen(
                    navController = navController
                )
            }
            composable("saved_list") {
                SavedRoutesListScreen(navController = navController)
            }
            composable(
                "summary?points={points}",
                arguments = listOf(navArgument("points") { type = NavType.StringType })
            ) { backStackEntry ->
                val pointsJson = backStackEntry.arguments?.getString("points") ?: ""
                RouteSummaryScreen(
                    pointsJson = Uri.decode(pointsJson),
                    navController = navController
                )
            }
            composable(
                "route_detail?routeId={routeId}",
                arguments = listOf(navArgument("routeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val routeId = backStackEntry.arguments?.getString("routeId") ?: return@composable
                SavedRouteDetailScreen(routeId = routeId, navController = navController)
            }
        }
    }
}