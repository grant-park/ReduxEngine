package ai.grant.reduxengine

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.launch
import org.junit.Test

class InstrumentedReduxEngineTest {

    @Test
    fun test() {
        val channel = ConflatedBroadcastChannel<State>()
        val reducer: Reducer = { state, action ->
            state
        }
        val epic: Epic = { action, state ->
            listOf(NoOpAction)
        }
        val middleware: Middleware = { action ->
            // do nothing
        }
        val action: Action = object : Action {}
        val initialState = object: State {}
        val subject = ReduxEngine(channel, reducer, epic, middleware)
        MainScope().launch {
            channel.send(initialState)
        }
        subject.dispatch(action)
    }
}
