package ai.grant.reduxengine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class ReduxEngineTest {

    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain() // reset main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }

    @Test
    fun test() {
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
        val subject = ReduxEngine(ConflatedBroadcastChannel(), reducer, epic, middleware)
        subject.dispatch(action)
    }
}
