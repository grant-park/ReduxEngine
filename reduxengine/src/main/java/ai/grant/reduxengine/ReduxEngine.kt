package ai.grant.reduxengine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

interface State

interface Action

interface Epic<S : State> {
    fun map(action: Action, state: S): Flow<Action>
}

interface Reducer<S : State> {
    fun reduce(action: Action, state: S): S
}

@ExperimentalCoroutinesApi
interface Store<S : State> {
    val stateChanges: ConflatedBroadcastChannel<S>
    fun dispatch(action: Action)
}

@ExperimentalCoroutinesApi
class ReduxEngine<S : State>(
    override val stateChanges: ConflatedBroadcastChannel<S>,
    private val reducer: Reducer<S>,
    private val epic: Epic<S>,
    private val mainDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher
) : Store<S>, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = mainDispatcher

    @ExperimentalCoroutinesApi
    fun warmUp(initialState: S) {
        launch {
            stateChanges.send(initialState)
        }
    }

    fun listen(action: (S) -> Unit) {
        launch {
            stateChanges.openSubscription().consumeEach(action) // unit test that most recent item is emitted
        }
    }

    @ExperimentalCoroutinesApi
    override fun dispatch(action: Action) {
        launch {
            stateChanges.send(
                reducer.reduce(action, stateChanges.value)
            )
        }.invokeOnCompletion { throwable ->
            throwable?.let { throw it } // unit test exceptions from reducer
            launch {
                withContext(ioDispatcher) { // unit test that correct thread is used
                    epic.map(action, stateChanges.value) // unit test exceptions from epic
                }.collect {
                    dispatch(it)
                }
            }
        }
    }
}
