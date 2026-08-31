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
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val MODEL_PATH = "/sdcard/LLM/gemma-4-E2B-it.litertlm"
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private lateinit var permissionHandler: PermissionHandler

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

        permissionHandler = PermissionHandler(this, ::loadLocalModel)
        permissionHandler.requestAccess()
    }

    override fun onResume() {
        super.onResume()

        permissionHandler.onResume()
    }

    private fun loadLocalModel() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(MODEL_PATH)

                require(file.isFile) {
                    "Model not found: ${file.absolutePath}"
                }

                Log.d("LiteRT", "Loading: ${file.absolutePath}")

                engine = Engine(
                    EngineConfig(
                        modelPath = file.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = cacheDir.absolutePath
                    )
                )


                startEngine()

                //Start Conversation with model
                sendPromptToModel()

            } catch (error: Exception) {
                Log.e("LiteRT", "Model loading failed", error)
            }
        }
    }

    private suspend fun startEngine(){
        // This can take several seconds. Keep it off the UI thread.
        engine?.initialize()
        Log.d("LiteRT", "Model loaded successfully")

    }
    private suspend fun sendPromptToModel(){
        // Optional: Configure the system instruction, initial messages, sampling
        // parameters, etc.
        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of("You are a helpful assistant."),
            initialMessages = listOf(
                Message.user("What is the capital city of the United States?"),
                Message.model("Washington, D.C."),
            ),
            samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8),
        )

        val conversation = engine?.createConversation(conversationConfig)
//        // Within a coroutine scope
//        conversation?.sendMessageAsync("What is the capital of France?")
//            ?.catch { } // Error during streaming
//            ?.collect { Log.d("Model Answer", "" +it.toString() )}

        val answer  = conversation?.sendMessage("What is the capital of France?")
        Log.d("Model Answer", "" +answer)

    }

    override fun onDestroy() {
        super.onDestroy()
        // Close the conversation when done
        conversation?.close()
        engine?.close()
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
