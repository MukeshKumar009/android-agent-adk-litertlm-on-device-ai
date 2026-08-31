package com.example.android_agent_adk_litertlm_on_device_ai

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.android_agent_adk_litertlm_on_device_ai.ui.theme.AndroidagentadklitertlmondeviceaiTheme
import com.google.adk.kt.litertlm.LiteRtLmModel
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private companion object{
        const val TAG = "Agent"
        const val MODEL_PATH = "/sdcard/LLM/gemma-4-E2B-it.litertlm"
        const val APP_NAME = "ADK Agent App"
    }

    private val sessionService = InMemorySessionService()
    private var runner: InMemoryRunner? = null
    private lateinit var permissionHandler: PermissionHandler
    private var question by mutableStateOf("")
    private var agentResponse by mutableStateOf("Setting up agent…")
    private var isRunnerReady by mutableStateOf(false)
    private var isAsking by mutableStateOf(false)

    /**
     * Coroutine scope for agent work. Coroutines launch on the default dispatcher; UI updates are
     * marshaled back via [runOnUiThread]. Cancelled in [onDestroy].
     */
    protected val scope = CoroutineScope(SupervisorJob())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidagentadklitertlmondeviceaiTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainLayout(
                        modelName = File(MODEL_PATH).nameWithoutExtension,
                        question = question,
                        response = agentResponse,
                        isRunnerReady = isRunnerReady,
                        isAsking = isAsking,
                        onQuestionChange = { question = it },
                        onAsk = ::askAgent,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }

        permissionHandler = PermissionHandler(this, ::setupAgent)
        permissionHandler.requestAccess()

    }

    /**
     * Set up the Agent with ADK and LiteRT-LM
     */
    private fun setupAgent(){
        // Off the main thread: looking for the model touches the filesystem.
        scope.launch(Dispatchers.IO) {

            //This can be your external or app data storage path
            val modelFile = File(MODEL_PATH)
            modelFile.let {
                //Init the model and runner with agent
                initRunner(modelFile)

                if (runner != null) {
                    runOnUiThread { isRunnerReady = true }
                    updateAgentResponse("Ask agent anything (Device model, battery percentage).")
                } else {
                    updateAgentResponse("Unable to initialize the model.")
                }
            }
        }

    }
    /**
     * Runner will be used to invoke prompt to agent
     */
    private fun initRunner(modelFile: File) {
        try {
            //Load model from file path
            val litertLmModel = AgentADK.createModelLiteRtLM(modelFile, cacheDir)
            Log.d(TAG, "Model loaded")
            // Initialize the engine with model
            litertLmModel.engine.initialize()
            // Only now is there a native engine to release; closing one that failed to open throws.
            releaseWithScope(litertLmModel)

            //Create our agent with ADK framework
            val agentADK = AgentADK.createAgent(litertLmModel, applicationContext);
            //Create a runner with our agent, this will be used for prompt injection
            runner =
                InMemoryRunner(
                    agent = agentADK,
                    appName = APP_NAME,
                    sessionService = sessionService,
                )

            Log.d(TAG, "Model Agent runner created")

        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * Send prompt to agent using runner
     */
    private fun askAgent() {
        val prompt = question.trim()
        if (prompt.isEmpty() || isAsking) return

        if (runner == null) {
            agentResponse = "The model is still loading. Please try again shortly."
            return
        }

        isAsking = true
        agentResponse = "Thinking…"
        scope.launch(Dispatchers.IO) {
            try {
                sendToAgent(prompt) { response -> updateAgentResponse(response) }
            } catch (error: Throwable) {
                Log.e(TAG, "Agent request failed", error)
                updateAgentResponse("Unable to get an answer: ${error.message}")
            } finally {
                runOnUiThread { isAsking = false }
            }
        }
    }

    private suspend fun sendToAgent(text: String, onResponse: (String) -> Unit) {
        runner?.runAsync(
            userId = "user-123",
            sessionId = "session-123",
            newMessage = Content(
                role = Role.USER,
                parts = listOf(Part(text = text)),
            ),
        )?.collect { event ->
            val text = event.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                Log.d(TAG, "Answer: $text")
                onResponse(text)
            }
        }
    }

    private fun updateAgentResponse(response: String) {
        runOnUiThread { agentResponse = response }
    }
    /**
     * Releases [model]'s native engine once the activity scope completes, which is after `onDestroy`
     * cancelled it and no turn is still running. Its own thread, because releasing is slow; failures
     * are swallowed, because an uncaught one here would take the process down.
     */
    private fun releaseWithScope(model: LiteRtLmModel) {
        scope.coroutineContext.job.invokeOnCompletion {
            thread(name = "litertlm-close") {
                try {
                    model.close()
                } catch (_: Throwable) {
                    // Nothing to report to: the screen this belonged to is already gone.
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionHandler.onResume()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

@Composable
fun MainLayout(
    modelName: String,
    question: String,
    response: String,
    isRunnerReady: Boolean,
    isAsking: Boolean,
    onQuestionChange: (String) -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Agent ADK On-Device", style = MaterialTheme.typography.headlineSmall)
        Text("Model Name: $modelName", style = MaterialTheme.typography.bodyMedium)

        Column(horizontalAlignment = Alignment.End) {
            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier.fillMaxWidth(),
                label = if (isRunnerReady) {
                    { Text("Ask something") }
                } else {
                    null
                },
                placeholder = {
                    Text(if (isRunnerReady) "Ask something" else "Loading model, please wait...")
                },
                enabled = isRunnerReady,
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onAsk,
                enabled = isRunnerReady && question.isNotBlank() && !isAsking,
            ) {
                Text(if (isAsking) "Asking…" else "Ask")
            }
        }

        Text("Agent Response", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = response,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainLayoutPreview() {
    AndroidagentadklitertlmondeviceaiTheme {
        MainLayout(
            modelName = "gemma-4-E2B-it",
            question = "",
            response = "Ask agent anything about device or battery.",
            isRunnerReady = true,
            isAsking = false,
            onQuestionChange = {},
            onAsk = {},
        )
    }
}
