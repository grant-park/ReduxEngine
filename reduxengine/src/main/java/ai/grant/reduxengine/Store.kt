package ai.grant.reduxengine

import ai.grant.reduxengine.core.Action
import ai.grant.reduxengine.core.Engine
import ai.grant.reduxengine.core.Epic
import ai.grant.reduxengine.core.Reducer
import ai.grant.reduxengine.core.State
import ai.grant.reduxengine.util.CompositeEpic
import ai.grant.reduxengine.util.CompositeReducer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel

@ExperimentalCoroutinesApi
object Store {
    private var ENGINE: Engine<out State>? = null

    fun <S : State> initialize(
        reducers: List<Reducer<S>>,
        epics: List<Epic<S>>,
        initialState: S,
        coroutineScope: CoroutineScope = MainScope(),
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        ENGINE = Engine(
            CompositeReducer(reducers),
            CompositeEpic(epics),
            coroutineScope,
            mainDispatcher,
            ioDispatcher,
            ConflatedBroadcastChannel(initialState)
        )
    }

    fun <S : State, T> listen(selector: (S) -> T, action: (T) -> Unit): ReceiveChannel<S> {
        return (ENGINE as Engine<S>).listen(selector, action)
    }

    fun <S : State> listen(action: (S) -> Unit): ReceiveChannel<S> {
        return (ENGINE as Engine<S>).listen(action)
    }

    fun dispatch(action: Action) {
        ENGINE!!.dispatch(action)
    }
}
