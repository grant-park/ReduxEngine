package ai.grant.reduxengine.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ExperimentalCoroutinesApi
class Engine<S : State> internal constructor(
    private val reducer: Reducer<S>,
    private val epic: Epic<S>,
    private val coroutineScope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateChanges: ConflatedBroadcastChannel<S>,
) {
    internal fun <T> listen(selector: (S) -> T, action: (T) -> Unit): ReceiveChannel<S> {
        val subscription = stateChanges.openSubscription()
        coroutineScope.launch(mainDispatcher) {
            subscription
                .receiveAsFlow()
                .map { selector(it) }
                .distinctUntilChanged()
                .onEach { action(it) }
                .collect()
        }
        return subscription
    }

    internal fun listen(action: (S) -> Unit): ReceiveChannel<S> {
        return listen({ i -> i }, action)
    }

    internal fun dispatch(action: Action) {
        coroutineScope.launch(mainDispatcher) {
            stateChanges.send(
                reducer.reduce(action, stateChanges.value)
            )
        }.invokeOnCompletion { throwable ->
            throwable?.let { throw it }
            coroutineScope.launch(mainDispatcher) {
                withContext(ioDispatcher) {
                    epic.map(action, stateChanges.value)
                }.collect {
                    dispatch(it)
                }
            }
        }
    }
}
