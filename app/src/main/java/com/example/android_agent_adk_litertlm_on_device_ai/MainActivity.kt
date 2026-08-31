package com.example.android_agent_adk_litertlm_on_device_ai

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    private val TAG = "Agent"
    private val MODEL_PATH = "/sdcard/LLM/gemma-4-E2B-it.litertlm"
    private val APP_NAME = "ADK Agent App"
    private val sessionService = InMemorySessionService()
    private var runner: InMemoryRunner? = null
    private lateinit var permissionHandler: PermissionHandler

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
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        permissionHandler = PermissionHandler(this, ::setupAgent)
        permissionHandler.requestAccess()

    }

    //Set up the Agent with ADK and LiteRT-LM
    private fun setupAgent(){
        // Off the main thread: looking for the model touches the filesystem.
        scope.launch(Dispatchers.IO) {

            //This can be your external or app data storage path
            val modelFile = File(MODEL_PATH)
            modelFile.let {
                //Init the model and runner with agent
                initRunner(modelFile)

                //Invoke prompt to agent
                runner.let {
                    // Call the agent from a coroutine (e.g. in a ViewModel or Activity)
                    Log.d(TAG, "Calling agent...")
                    sendToAgent(text = "What's the battery percentage?")
                }
            }
        }

    }
    //Runner will be used to invoke prompt to agent
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

    //Send prompt to agent using runner
    private suspend fun sendToAgent(text: String) {
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
                // Update your UI with the agent's response
                Log.d(TAG, "Answer: $text")
            }
        }
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
fun MainLayout(modifier: Modifier = Modifier) {
    Text(
        text = "On Device Agent",
        modifier = modifier.padding(24.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun MainLayoutPreview() {
    AndroidagentadklitertlmondeviceaiTheme {
        MainLayout()
    }
}
