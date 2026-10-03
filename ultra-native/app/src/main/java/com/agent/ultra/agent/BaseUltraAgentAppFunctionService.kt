package com.agent.ultra.agent

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.agent.ultra.local.LocalModelEngine

/**
 * Android 16 AppFunctions bridge for trusted on-device assistants.
 *
 * This intentionally exposes one high-level capability instead of duplicating
 * the agent's individual device tools. Brain.run() remains the single policy
 * and execution path, including the existing Gate/confirmation controls.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
    serviceName = "UltraAgentAppFunctionService",
    appFunctionXmlFileName = "ultra_agent_app_function_service",
)
abstract class BaseUltraAgentAppFunctionService : AppFunctionService() {

    /**
     * Execute a user-requested task through Agent Ultra.
     *
     * Use this for concrete actions on the user's phone. The request is treated
     * exactly like text entered into Agent Ultra; policy checks and explicit
     * confirmations are still enforced by Brain. Do not use this to access
     * secrets or perform an action the user did not request.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun runTask(task: String): String = withContext(Dispatchers.Default) {
        val engine = LocalModelEngine.shared(applicationContext)
        val brain = com.agent.ultra.agent.Brain(applicationContext, engine)
        var answer: String? = null
        brain.onAnswer = { answer = it }

        val completed = withTimeoutOrNull(120_000L) {
            brain.run(task.trim())
            true
        } ?: false

        if (!completed) {
            return@withContext "Task timed out while Agent Ultra was working."
        }
        if (brain.pendingConfirm != null) {
            return@withContext "Agent Ultra paused for confirmation: ${brain.pendingConfirm?.description ?: "action requires confirmation"}. Open Agent Ultra to approve or cancel."
        }
        answer ?: "Agent Ultra finished the task without a final text response."
    }
}
