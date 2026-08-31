package com.example.android_agent_adk_litertlm_on_device_ai

import android.content.Context
import com.example.android_agent_adk_litertlm_on_device_ai.AgentADK.NAME
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.litertlm.DefaultLiteRtLmEngine
import com.google.adk.kt.litertlm.LiteRtLmModel
import com.google.adk.kt.models.Model
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File


/**
 * Builds the on-device model and the [LlmAgent] used by [MainActivity]. The model is
 * created separately because it owns a native engine, whose lifetime the activity ties to its own.
 */
internal object AgentADK {
    const val NAME: String = "agent_adk"

    /**
     * Opens [modelFile] on the CPU backend; the returned model owns a native engine and must be
     * closed. [cacheDir] puts the compiled-model cache where the system can reclaim it.
     */
    fun createModelLiteRtLM(modelFile: File, cacheDir: File): LiteRtLmModel =
        LiteRtLmModel.create(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = cacheDir.absolutePath,
            ),
            name = modelFile.name,
        )

    /** Builds the agent around an already-created [model], with [context]'s device tools. */
    fun createAgent(model: LiteRtLmModel, context: Context): LlmAgent =
        LlmAgent(
            name = NAME,
            model = model,
            instruction =
                Instruction(
                    """
          You are a helpful assistant running entirely on this device. Keep replies to one or two
          short sentences. 
          Use tools for device specific answers : call get_battery_level when the user asks about the battery, and
          get_device_info when they ask what device this is, then state the exact value the tool
          returned.
          If you know the answer of generic questions other than tool then you can answer other wise say "Sorry, I don't know much about it."
          """
                        .trimIndent()
                ),
            tools = DeviceTools(context).generatedTools(),
        )
}