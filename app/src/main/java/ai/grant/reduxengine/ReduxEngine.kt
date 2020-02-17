package ai.grant.reduxengine

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import java.lang.Exception

interface State
interface Action
object NoOpAction : Action
typealias Epic = (Action, State) -> List<Action>
typealias Reducer = (State, Action) -> State

interface Store {
    val stateChanges: ConflatedBroadcastChannel<State>
    fun dispatch(action: Action)
}

class ReduxEngine(
    override val stateChanges: ConflatedBroadcastChannel<State>,
    private val reducer: Reducer,
    private val epic: Epic
) : Store, CoroutineScope by MainScope() {
    override fun dispatch(action: Action) {
        if (action !is NoOpAction) {
            launch {
                stateChanges.send(
                    reducer(stateChanges.value, action)
                )
            }.invokeOnCompletion { throwable ->
                if (throwable == null) {
                    launch {
                        try {
                            withContext(Dispatchers.IO) {
                                epic(action, stateChanges.value)
                            }.forEach {
                                dispatch(it)
                            }
                        } catch (exception: Exception) {
                            throw Exception("Epic exception")
                        }
                    }
                } else {
                    throw Exception("Reducer exception")
                }
            }
        }
    }
}

