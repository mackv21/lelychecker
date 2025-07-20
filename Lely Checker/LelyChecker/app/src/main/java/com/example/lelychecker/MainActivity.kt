package com.example.lelychecker

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.IOException
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.DropdownMenuItem
import androidx.compose.runtime.snapshots.SnapshotStateMap
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.RadioButtonDefaults
import com.example.lelychecker.R

data class Task(
    val taskOrder: Double,
    val machine: String,
    val task: String,
    val lelyTaskNumber: Double?,
    val guide: String?,
    val option: String?,
    val services: List<String>,
    val info: String?
)

data class MachineInfo(
    val type: String,
    val serial: String,
    val ldn: String,
    val options: String
)

@Composable
fun LelyCheckerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val grey = Color(ContextCompat.getColor(context, R.color.grey))
    val red = Color(ContextCompat.getColor(context, R.color.red))
    val white = Color(ContextCompat.getColor(context, R.color.white))

    MaterialTheme(
        colors = lightColors(
            primary = red,
            onPrimary = white,
            background = grey,
            surface = grey,
            onBackground = white,
            onSurface = white,
            secondary = red,
            onSecondary = white
        ),
        typography = Typography(
            h4 = TextStyle(color = white, fontSize = 34.sp),
            h6 = TextStyle(color = white, fontSize = 20.sp),
            subtitle1 = TextStyle(color = white, fontSize = 16.sp),
            body1 = TextStyle(color = white, fontSize = 16.sp),
            caption = TextStyle(color = white, fontSize = 12.sp)
        ),
        content = content
    )
}

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    var cachedTasks: List<Task>? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        lifecycleScope.launch {
            cachedTasks = loadTasks()
            setContent {
                LelyCheckerTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colors.background
                    ) {
                        LelyCheckerApp(cachedTasks = cachedTasks ?: emptyList())
                    }
                }
            }
        }
    }

    private suspend fun loadTasks(): List<Task> = withContext(Dispatchers.IO) {
        try {
            val tasksJson = assets.open("tasks.json").use { InputStreamReader(it).readText() }
            Log.d(TAG, "tasks.json loaded, size: ${tasksJson.length} bytes")
            Gson().fromJson(tasksJson, Array<Task>::class.java).toList().also {
                Log.d(TAG, "Parsed ${it.size} tasks")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load tasks.json: ${e.message}")
            runOnUiThread { Toast.makeText(this@MainActivity, "Error loading tasks: ${e.message}", Toast.LENGTH_LONG).show() }
            emptyList()
        } catch (e: com.google.gson.JsonSyntaxException) {
            Log.e(TAG, "Failed to parse tasks.json: ${e.message}")
            runOnUiThread { Toast.makeText(this@MainActivity, "Invalid tasks.json format", Toast.LENGTH_LONG).show() }
            emptyList()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult called")
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents != null) {
            Log.d(TAG, "QR code scanned: ${result.contents}")
        } else {
            Log.d(TAG, "QR scan failed or cancelled")
        }
    }
}

@Composable
fun LelyCheckerApp(cachedTasks: List<Task>) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val TAG = "LelyCheckerApp"

    var technicianName by remember { mutableStateOf("Paul") }
    var serviceCenter by remember { mutableStateOf("Ayr") }
    var date by remember { mutableStateOf("07/19/2025") }
    var machineInfo by remember { mutableStateOf(MachineInfo("", "", "", "")) }
    var serviceInput by remember { mutableStateOf(TextFieldValue("A")) }
    var qrResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(qrResult) {
        qrResult?.let { contents ->
            try {
                val json = JSONObject(contents)
                machineInfo = MachineInfo(
                    type = json.optString("type", "").takeIf { it.isNotBlank() && it.length <= 100 } ?: "",
                    serial = json.optString("serial", "").takeIf { it.isNotBlank() && it.length <= 100 } ?: "",
                    ldn = json.optString("ldn", "").takeIf { it.isNotBlank() && it.length <= 100 } ?: "",
                    options = json.optString("options", "").takeIf { it.isNotBlank() && it.length <= 100 } ?: ""
                )
                serviceInput = TextFieldValue(json.optString("service", "A").takeIf { it.isNotBlank() && it.length <= 100 } ?: "A")
                Log.d(TAG, "Parsed QR: machineInfo=$machineInfo, service=${serviceInput.text}")
                navController.navigate("service_selection")
            } catch (e: JSONException) {
                Log.e(TAG, "Failed to parse QR code JSON: ${e.message}")
                Toast.makeText(context, "Invalid QR code format", Toast.LENGTH_LONG).show()
            }
        }
    }

    NavHost(navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen { navController.navigate("user_details") }
        }
        composable("user_details") {
            UserDetailsScreen(
                technicianName = technicianName,
                serviceCenter = serviceCenter,
                date = date,
                onTechnicianChange = { technicianName = it },
                onServiceCenterChange = { serviceCenter = it },
                onDateChange = { date = it },
                onSubmit = { navController.navigate("entry_choice") }
            )
        }
        composable("entry_choice") {
            EntryChoiceScreen(
                onScanQR = {
                    Log.d(TAG, "Starting QR scan")
                    IntentIntegrator(context as MainActivity).initiateScan()
                    qrResult = null
                },
                onManualEntry = { navController.navigate("machine_form") }
            )
        }
        composable("machine_form") {
            MachineFormScreen(
                machineInfo = machineInfo,
                onMachineInfoChange = { machineInfo = it },
                onSubmit = { navController.navigate("service_selection") }
            )
        }
        composable("service_selection") {
            ServiceSelectionScreen(
                serviceInput = serviceInput,
                onServiceChange = { serviceInput = it },
                onSubmit = { navController.navigate("checklist") }
            )
        }
        composable("checklist") {
            ChecklistScreen(
                tasks = cachedTasks,
                machine = machineInfo.type,
                service = serviceInput.text,
                technicianName = technicianName,
                serviceCenter = serviceCenter,
                date = date,
                onSubmit = { navController.navigate("report_preview") }
            )
        }
        composable("report_preview") {
            ReportPreviewScreen(
                tasks = cachedTasks,
                machineInfo = machineInfo,
                service = serviceInput.text,
                technicianName = technicianName,
                serviceCenter = serviceCenter,
                date = date,
                generateQrCode = machineInfo.type.isNotBlank() && qrResult == null,
                onDownload = { navController.navigate("splash") }
            )
        }
    }
}

@Composable
fun SplashScreen(onNavigate: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onNavigate()
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("LelyChecker", style = MaterialTheme.typography.h4)
    }
}

@Composable
fun UserDetailsScreen(
    technicianName: String,
    serviceCenter: String,
    date: String,
    onTechnicianChange: (String) -> Unit,
    onServiceCenterChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val TAG = "UserDetailsScreen"
    Log.d(TAG, "Rendering UserDetailsScreen")

    Scaffold(
        topBar = { TopAppBar(title = { Text("Enter User Details") }, backgroundColor = MaterialTheme.colors.primary) },
        backgroundColor = MaterialTheme.colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = TextFieldValue(technicianName),
                onValueChange = { onTechnicianChange(it.text.take(100)) },
                label = { Text("Technician Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.textFieldColors(
                    textColor = MaterialTheme.colors.onBackground,
                    backgroundColor = MaterialTheme.colors.background,
                    focusedIndicatorColor = MaterialTheme.colors.primary,
                    unfocusedIndicatorColor = MaterialTheme.colors.onBackground
                )
            )
            TextField(
                value = TextFieldValue(serviceCenter),
                onValueChange = { onServiceCenterChange(it.text.take(100)) },
                label = { Text("Service Center") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.textFieldColors(
                    textColor = MaterialTheme.colors.onBackground,
                    backgroundColor = MaterialTheme.colors.background,
                    focusedIndicatorColor = MaterialTheme.colors.primary,
                    unfocusedIndicatorColor = MaterialTheme.colors.onBackground
                )
            )
            TextField(
                value = TextFieldValue(date),
                onValueChange = { onDateChange(it.text.take(100)) },
                label = { Text("Date (MM/dd/yyyy)") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.textFieldColors(
                    textColor = MaterialTheme.colors.onBackground,
                    backgroundColor = MaterialTheme.colors.background,
                    focusedIndicatorColor = MaterialTheme.colors.primary,
                    unfocusedIndicatorColor = MaterialTheme.colors.onBackground
                )
            )
            Button(
                onClick = {
                    Log.d(TAG, "Submit clicked")
                    onSubmit()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary
                )
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
fun EntryChoiceScreen(onScanQR: () -> Unit, onManualEntry: () -> Unit) {
    val TAG = "EntryChoiceScreen"
    Log.d(TAG, "Rendering EntryChoiceScreen")

    Scaffold(
        topBar = { TopAppBar(title = { Text("Choose Entry Method") }, backgroundColor = MaterialTheme.colors.primary) },
        backgroundColor = MaterialTheme.colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onScanQR,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary
                )
            ) {
                Text("Scan QR Code")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onManualEntry,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary
                )
            ) {
                Text("Enter Manually")
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MachineFormScreen(
    machineInfo: MachineInfo,
    onMachineInfoChange: (MachineInfo) -> Unit,
    onSubmit: () -> Unit
) {
    val TAG = "MachineFormScreen"
    Log.d(TAG, "Rendering MachineFormScreen")

    // Machine type dropdown
    val machineTypes = listOf("A5RU", "A5CU", "A4RU", "A4CU", "A3RU")
    var expanded by remember { mutableStateOf(false) }
    var selectedMachineType by remember { mutableStateOf(machineInfo.type) }

    // Options checkboxes
    val optionsList = listOf(
        "Air Compressor", "Airbuffer", "Lubricator", "M4USe", "Meteor",
        "MQC-C", "Pura", "Single Filter", "Sleeves", "Twin Filter"
    )
    val selectedOptions = remember {
        mutableStateMapOf<String, Boolean>().apply {
            optionsList.forEach { option ->
                put(option, machineInfo.options.split(",").map { it.trim() }.contains(option))
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Enter Machine Details") }, backgroundColor = MaterialTheme.colors.primary) },
        backgroundColor = MaterialTheme.colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Machine Type Dropdown
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = selectedMachineType,
                    onValueChange = { /* Read-only, handled by dropdown selection */ },
                    label = { Text("Machine Type") },
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = TextFieldDefaults.textFieldColors(
                        textColor = MaterialTheme.colors.onBackground,
                        backgroundColor = MaterialTheme.colors.background,
                        focusedIndicatorColor = MaterialTheme.colors.primary,
                        unfocusedIndicatorColor = MaterialTheme.colors.onBackground,
                        trailingIconColor = MaterialTheme.colors.onBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = androidx.compose.ui.Modifier.background(MaterialTheme.colors.background)
                ) {
                    machineTypes.forEach { type ->
                        DropdownMenuItem(
                            onClick = {
                                selectedMachineType = type
                                onMachineInfoChange(
                                    machineInfo.copy(
                                        type = type,
                                        options = selectedOptions.filter { it.value }.keys.joinToString(", ")
                                    )
                                )
                                expanded = false
                                Log.d(TAG, "Selected machine type: $type")
                            }
                        ) {
                            Text(
                                text = type,
                                style = MaterialTheme.typography.body1,
                                color = MaterialTheme.colors.onBackground
                            )
                        }
                    }
                }
            }

            // Serial Number
            TextField(
                value = TextFieldValue(machineInfo.serial),
                onValueChange = {
                    onMachineInfoChange(
                        machineInfo.copy(
                            serial = it.text.take(100),
                            options = selectedOptions.filter { it.value }.keys.joinToString(", ")
                        )
                    )
                },
                label = { Text("Serial Number") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.textFieldColors(
                    textColor = MaterialTheme.colors.onBackground,
                    backgroundColor = MaterialTheme.colors.background,
                    focusedIndicatorColor = MaterialTheme.colors.primary,
                    unfocusedIndicatorColor = MaterialTheme.colors.onBackground
                )
            )

            // LDN
            TextField(
                value = TextFieldValue(machineInfo.ldn),
                onValueChange = {
                    onMachineInfoChange(
                        machineInfo.copy(
                            ldn = it.text.take(100),
                            options = selectedOptions.filter { it.value }.keys.joinToString(", ")
                        )
                    )
                },
                label = { Text("LDN") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.textFieldColors(
                    textColor = MaterialTheme.colors.onBackground,
                    backgroundColor = MaterialTheme.colors.background,
                    focusedIndicatorColor = MaterialTheme.colors.primary,
                    unfocusedIndicatorColor = MaterialTheme.colors.onBackground
                )
            )

            // Options Checkboxes (2 columns)
            Text("Options", style = MaterialTheme.typography.subtitle1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    optionsList.take(5).forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = selectedOptions[option] ?: false,
                                onCheckedChange = { isChecked ->
                                    if (option == "Single Filter" && isChecked) {
                                        selectedOptions["Twin Filter"] = false
                                    } else if (option == "Twin Filter" && isChecked) {
                                        selectedOptions["Single Filter"] = false
                                    }
                                    selectedOptions[option] = isChecked
                                    onMachineInfoChange(
                                        machineInfo.copy(
                                            options = selectedOptions.filter { it.value }.keys.joinToString(", ")
                                        )
                                    )
                                    Log.d(TAG, "Option $option checked: $isChecked")
                                },
                                colors = CheckboxDefaults.colors(
                                    checkmarkColor = MaterialTheme.colors.onBackground,
                                    checkedColor = MaterialTheme.colors.primary,
                                    uncheckedColor = MaterialTheme.colors.onBackground
                                )
                            )
                            Text(
                                text = option,
                                style = MaterialTheme.typography.body1,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    optionsList.drop(5).forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = selectedOptions[option] ?: false,
                                onCheckedChange = { isChecked ->
                                    if (option == "Single Filter" && isChecked) {
                                        selectedOptions["Twin Filter"] = false
                                    } else if (option == "Twin Filter" && isChecked) {
                                        selectedOptions["Single Filter"] = false
                                    }
                                    selectedOptions[option] = isChecked
                                    onMachineInfoChange(
                                        machineInfo.copy(
                                            options = selectedOptions.filter { it.value }.keys.joinToString(", ")
                                        )
                                    )
                                    Log.d(TAG, "Option $option checked: $isChecked")
                                },
                                colors = CheckboxDefaults.colors(
                                    checkmarkColor = MaterialTheme.colors.onBackground,
                                    checkedColor = MaterialTheme.colors.primary,
                                    uncheckedColor = MaterialTheme.colors.onBackground
                                )
                            )
                            Text(
                                text = option,
                                style = MaterialTheme.typography.body1,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            // Submit Button
            Button(
                onClick = {
                    Log.d(TAG, "Submit clicked: machineInfo=$machineInfo")
                    onSubmit()
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary
                )
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
fun ServiceSelectionScreen(
    serviceInput: TextFieldValue,
    onServiceChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit
) {
    val TAG = "ServiceSelectionScreen"
    Log.d(TAG, "Rendering ServiceSelectionScreen")
    val services = listOf("A", "B", "C", "D")
    var selectedService by remember { mutableStateOf(serviceInput.text) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Select Service Type") }, backgroundColor = MaterialTheme.colors.primary) },
        backgroundColor = MaterialTheme.colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            services.forEach { service ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedService = service
                            onServiceChange(TextFieldValue(service))
                            Log.d(TAG, "Selected service: $service")
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedService == service,
                        onClick = {
                            selectedService = service
                            onServiceChange(TextFieldValue(service))
                            Log.d(TAG, "Selected service: $service")
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colors.primary,
                            unselectedColor = MaterialTheme.colors.onBackground
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(service)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    Log.d(TAG, "Submit clicked: service=$selectedService")
                    onSubmit()
                },
                enabled = selectedService.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary
                )
            ) {
                Text("Show Checklist")
            }
        }
    }
}

@Composable
fun ChecklistScreen(
    tasks: List<Task>,
    machine: String,
    service: String,
    technicianName: String,
    serviceCenter: String,
    date: String,
    onSubmit: () -> Unit
) {
    val TAG = "ChecklistScreen"
    val context = LocalContext.current
    Log.d(TAG, "Rendering ChecklistScreen with machine=$machine, service=$service")

    val sharedPrefs = context.getSharedPreferences("LelyCheckerPrefs", Context.MODE_PRIVATE)
    val filteredTasks by remember(tasks, machine, service) {
        derivedStateOf {
            tasks.filter { task ->
                task.machine.split(",").any { it.trim() == machine } && task.services.contains(service)
            }.sortedBy { it.taskOrder }
        }
    }
    Log.d(TAG, "Filtered ${filteredTasks.size} tasks")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Checklist for $machine (Service $service)")
                        Text(
                            text = "Technician: $technicianName, Center: $serviceCenter, Date: $date",
                            style = MaterialTheme.typography.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                backgroundColor = MaterialTheme.colors.primary
            )
        },
        backgroundColor = MaterialTheme.colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredTasks.isEmpty()) {
                    item {
                        Text("No tasks found for the specified machine and service.", color = MaterialTheme.colors.onBackground)
                        Log.d(TAG, "No tasks found")
                    }
                } else {
                    items(filteredTasks, key = { it.taskOrder }) { task ->
                        TaskItem(task, sharedPrefs)
                    }
                }
            }
            Button(
                onClick = {
                    Log.d(TAG, "Submit clicked")
                    onSubmit()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary
                )
            ) {
                Text("Generate Report")
            }
        }
    }
}

@Composable
fun TaskItem(task: Task, sharedPrefs: android.content.SharedPreferences) {
    val TAG = "TaskItem"
    val context = LocalContext.current
    val checkedKey = "task_${task.taskOrder}_checked"
    val remarksKey = "task_${task.taskOrder}_remarks"
    var isChecked by remember { mutableStateOf(sharedPrefs.getBoolean(checkedKey, false)) }
    var isRemarksOpen by remember { mutableStateOf(false) }
    var remarks by remember { mutableStateOf(TextFieldValue(sharedPrefs.getString(remarksKey, "") ?: "")) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = {
                            isChecked = it
                            sharedPrefs.edit().putBoolean(checkedKey, it).apply()
                            Log.d(TAG, "Checkbox for task ${task.taskOrder} set to $it")
                        },
                        colors = CheckboxDefaults.colors(
                            checkmarkColor = MaterialTheme.colors.onBackground,
                            checkedColor = MaterialTheme.colors.primary,
                            uncheckedColor = MaterialTheme.colors.onBackground
                        )
                    )
                    Text(
                        text = task.task,
                        style = MaterialTheme.typography.body1,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Guide",
                        tint = if (task.guide != null) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(enabled = task.guide != null) {
                                task.guide?.let {
                                    Log.d(TAG, "Opening guide URL: $it")
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(it))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to open guide URL: ${e.message}")
                                    }
                                }
                            }
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Remarks",
                        tint = MaterialTheme.colors.onBackground,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                isRemarksOpen = !isRemarksOpen
                                Log.d(TAG, "Remarks toggled for task ${task.taskOrder}: $isRemarksOpen")
                            }
                    )
                }
            }
            if (isRemarksOpen) {
                TextField(
                    value = remarks,
                    onValueChange = {
                        remarks = it
                        sharedPrefs.edit().putString(remarksKey, it.text).apply()
                        Log.d(TAG, "Remarks updated for task ${task.taskOrder}: ${it.text}")
                    },
                    label = { Text("Technician Remarks") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = TextFieldDefaults.textFieldColors(
                        textColor = MaterialTheme.colors.onBackground,
                        backgroundColor = MaterialTheme.colors.background,
                        focusedIndicatorColor = MaterialTheme.colors.primary,
                        unfocusedIndicatorColor = MaterialTheme.colors.onBackground
                    )
                )
            }
        }
    }
}

@Composable
fun ReportPreviewScreen(
    tasks: List<Task>,
    machineInfo: MachineInfo,
    service: String,
    technicianName: String,
    serviceCenter: String,
    date: String,
    generateQrCode: Boolean,
    onDownload: () -> Unit
) {
    val TAG = "ReportPreviewScreen"
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("LelyCheckerPrefs", Context.MODE_PRIVATE)
    val filteredTasks by remember(tasks, machineInfo.type, service) {
        derivedStateOf {
            tasks.filter { task ->
                task.machine.split(",").any { it.trim() == machineInfo.type } && task.services.contains(service)
            }.sortedBy { it.taskOrder }
        }
    }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = android.graphics.Paint()
        paint.textSize = 12f
        paint.color = ContextCompat.getColor(context, R.color.black)
        var y = 50f

        canvas.drawText("LelyChecker Report", 50f, y, paint)
        y += 20f
        canvas.drawText("Technician: $technicianName", 50f, y, paint)
        y += 20f
        canvas.drawText("Service Center: $serviceCenter", 50f, y, paint)
        y += 20f
        canvas.drawText("Date: $date", 50f, y, paint)
        y += 20f
        canvas.drawText("Machine: ${machineInfo.type}, Serial: ${machineInfo.serial}, LDN: ${machineInfo.ldn}, Options: ${machineInfo.options}", 50f, y, paint)
        y += 20f
        canvas.drawText("Service: $service", 50f, y, paint)
        y += 20f
        canvas.drawText("Checklist:", 50f, y, paint)
        y += 20f

        filteredTasks.forEach { task ->
            val checked = sharedPrefs.getBoolean("task_${task.taskOrder}_checked", false)
            val remarks = sharedPrefs.getString("task_${task.taskOrder}_remarks", "") ?: ""
            canvas.drawText("${task.task}: ${if (checked) "Completed" else "Not Completed"}", 50f, y, paint)
            y += 15f
            if (remarks.isNotBlank()) {
                canvas.drawText("Remarks: $remarks", 60f, y, paint)
                y += 15f
            }
            y += 5f
        }

        document.finishPage(page)
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "LelyChecker_Report_${System.currentTimeMillis()}.pdf")
        document.writeTo(FileOutputStream(file))
        document.close()
        pdfFile = file
        Log.d(TAG, "PDF generated: ${file.absolutePath}")

        if (generateQrCode) {
            val json = JSONObject().apply {
                put("type", machineInfo.type)
                put("serial", machineInfo.serial)
                put("ldn", machineInfo.ldn)
                put("options", machineInfo.options)
                put("service", service)
            }
            val encoder = BarcodeEncoder()
            qrBitmap = encoder.encodeBitmap(json.toString(), BarcodeFormat.QR_CODE, 200, 200)
            Log.d(TAG, "QR code generated for manual entry")
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Report Preview") }, backgroundColor = MaterialTheme.colors.primary) },
        backgroundColor = MaterialTheme.colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Report Details", style = MaterialTheme.typography.h6)
            Text("Technician: $technicianName")
            Text("Service Center: $serviceCenter")
            Text("Date: $date")
            Text("Machine: ${machineInfo.type}, Serial: ${machineInfo.serial}")
            Text("LDN: ${machineInfo.ldn}, Options: ${machineInfo.options}")
            Text("Service: $service")
            Spacer(modifier = Modifier.height(16.dp))
            Text("Checklist:", style = MaterialTheme.typography.subtitle1)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredTasks, key = { it.taskOrder }) { task ->
                    val checked = sharedPrefs.getBoolean("task_${task.taskOrder}_checked", false)
                    val remarks = sharedPrefs.getString("task_${task.taskOrder}_remarks", "") ?: ""
                    Text("${task.task}: ${if (checked) "Completed" else "Not Completed"}")
                    if (remarks.isNotBlank()) {
                        Text("Remarks: $remarks", modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
            if (generateQrCode && qrBitmap != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("QR Code for Machine Info:", style = MaterialTheme.typography.subtitle1)
                Image(
                    bitmap = qrBitmap!!.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(200.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    pdfFile?.let { file ->
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open PDF"))
                        Log.d(TAG, "Opening PDF: ${file.absolutePath}")
                    }
                    onDownload()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = pdfFile != null,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary
                )
            ) {
                Text("Download and Finish")
            }
        }
    }
}
