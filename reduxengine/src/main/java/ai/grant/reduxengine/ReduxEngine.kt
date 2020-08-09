package ai.grant.reduxengine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

interface State
interface Action
interface Epic<S : State> {
    fun map(action: Action, state: S): Flow<Action>
}
interface Reducer<S : State> {
    fun reduce(action: Action, state: S): S
}
interface Store<S : State> {
    val stateChanges: ConflatedBroadcastChannel<S>
    fun dispatch(action: Action)
}
object NoOpAction : Action

class ReduxEngine<S : State>(
    override val stateChanges: ConflatedBroadcastChannel<S>,
    private val reducer: Reducer<S>,
    private val epic: Epic<S>
) : Store<S>, CoroutineScope by MainScope() {

    fun warmUp(initialState: S) {
        launch {
            stateChanges.send(initialState)
        }
    }

    fun listen(action: (S) -> Unit) {
        launch {
            stateChanges.consumeEach(action)
        }
    }

    override fun dispatch(action: Action) {
        if (action !is NoOpAction) {
            launch {
                stateChanges.send(
                    reducer.reduce(action, stateChanges.value)
                )
            }.invokeOnCompletion { throwable ->
                throwable?.let { throw it }
                launch {
                    withContext(Dispatchers.IO) {
                        epic.map(action, stateChanges.value)
                    }.collect {
                        dispatch(it)
                    }
                }
            }
        }
    }
}
