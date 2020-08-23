package ai.grant.reduxengine

import ai.grant.reduxengine.core.Action
import ai.grant.reduxengine.core.Engine
import ai.grant.reduxengine.core.Epic
import ai.grant.reduxengine.core.Reducer
import ai.grant.reduxengine.core.State
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineExceptionHandler
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class EngineTest {

    private val testIoDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
    private val testMainDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()
    private val testScope: CoroutineScope = TestCoroutineScope(Job())
    private val epic: Epic<ExampleState> = ExampleEpic()
    private val reducer: Reducer<ExampleState> = ExampleReducer()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testMainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        testMainDispatcher.cleanupTestCoroutines()
        testIoDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun listen() = runBlockingTest {
        // Pre-conditions
        val results = ArrayList<ExampleState>()
        val stateChanges: ConflatedBroadcastChannel<ExampleState> = ConflatedBroadcastChannel()
        val subject: Engine<ExampleState> = Engine(
            reducer,
            epic,
            testScope,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        val initialState = ExampleState(0)
        val nextState = ExampleState(1)

        // Execution
        subject.listen {
            results.add(it)
        }
        stateChanges.send(initialState)
        stateChanges.send(nextState)

        // Post-conditions
        assertEquals(2, results.size)
        assertEquals(initialState, results[0])
        assertEquals(nextState, results[1])
    }

    @Test
    fun dispatch() = runBlockingTest {
        // Pre-conditions
        val results = ArrayList<ExampleState>()
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> =
            ConflatedBroadcastChannel(initialState)
        val subject: Engine<ExampleState> = Engine(
            reducer,
            epic,
            testScope,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        val listeningJob = launch {
            stateChanges.openSubscription().consumeEach {
                results.add(it)
            }
        }

        // Execution
        subject.dispatch(ExampleAction.AddAction(2))

        // Post-conditions
        assertEquals(2, results.size)
        assertEquals(initialState, results[0])
        assertEquals(ExampleState(2), results[1])
        listeningJob.cancel()
    }

    @Test
    fun getStateChanges() = runBlockingTest {
        // Pre-conditions
        val results = ArrayList<ExampleState>()
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> =
            ConflatedBroadcastChannel(initialState)
        val subject: Engine<ExampleState> = Engine(
            reducer,
            epic,
            testScope,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        val nextState = ExampleState(1)

        // Execution
        subject.listen {
            results.add(it)
        }
        stateChanges.send(initialState)
        stateChanges.send(nextState)

        // Post-conditions
        assertEquals(2, results.size)
        assertEquals(initialState, results[0])
        assertEquals(nextState, results[1])
    }

    @Test
    fun `Most recent state is given when subscribing`() = runBlockingTest {
        // Pre-conditions
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> =
            ConflatedBroadcastChannel(initialState)
        val subject: Engine<ExampleState> = Engine(
            reducer,
            epic,
            testScope,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        subject.dispatch(ExampleAction.AddAction(2))
        subject.dispatch(ExampleAction.AddAction(2))
        val results = ArrayList<ExampleState>()

        // Execution
        subject.listen {
            results.add(it)
        }

        // Post-conditions
        assertEquals(1, results.size)
        assertEquals(ExampleState(4), results[0])
    }

    @Test
    fun `Only distinct updates to selected parts of state are listened for`() = runBlockingTest {
        // Pre-conditions
        val initialState = ExampleState(0, ExampleSubState(0))
        val stateChanges: ConflatedBroadcastChannel<ExampleState> =
            ConflatedBroadcastChannel(initialState)
        val subject: Engine<ExampleState> = Engine(
            { action, state ->
                when (action) {
                    is ExampleAction.AddAction -> state.copy(
                        subState = state.subState?.copy(subValue = state.subState.subValue + action.value)
                    )
                    is ExampleAction.SubtractAction -> state.copy(
                        subState = state.subState?.copy(subValue = state.subState.subValue - action.value)
                    )
                    else -> state
                }
            },
            epic,
            testScope,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        val results = ArrayList<Int?>()

        // Execution
        subject.listen({ it.subState }) {
            results.add(it?.subValue)
        }
        subject.dispatch(ExampleAction.AddAction(2))
        subject.dispatch(ExampleAction.AddAction(2))
        subject.dispatch(ExampleAction.AddAction(0))

        // Post-conditions
        assertEquals(listOf(0, 2, 4), results)
    }

    @Test
    fun `Only distinct updates to whole state are listened for`() = runBlockingTest {
        // Pre-conditions
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> =
            ConflatedBroadcastChannel(initialState)
        val subject: Engine<ExampleState> = Engine(
            reducer,
            epic,
            testScope,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        val results = ArrayList<Int>()

        // Execution
        subject.listen {
            results.add(it.value)
        }
        subject.dispatch(ExampleAction.AddAction(2))
        subject.dispatch(ExampleAction.AddAction(2))
        subject.dispatch(ExampleAction.AddAction(0))
        subject.dispatch(ExampleAction.AddAction(0))

        // Post-conditions
        assertEquals(listOf(0, 2, 4), results)
    }

    @Test
    fun `No more states received after cancelling subscription`() = runBlockingTest {
        // Pre-conditions
        val results: ArrayList<ExampleState> = ArrayList()
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> =
            ConflatedBroadcastChannel(initialState)
        val subject: Engine<ExampleState> = Engine(
            reducer,
            epic,
            testScope,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        subject.dispatch(ExampleAction.AddAction(2))
        subject.dispatch(ExampleAction.AddAction(2))

        // Execution
        val subscription = subject.listen {
            results.add(it)
        }
        subscription.cancel()
        subject.dispatch(ExampleAction.AddAction(2))
        subject.dispatch(ExampleAction.AddAction(2))

        // Post-conditions
        assertEquals(1, results.size)
    }

    @Test
    fun `ReduxEngine is off after cancelling job`() = runBlockingTest {
        // Pre-conditions
        val results: ArrayList<ExampleState> = ArrayList()
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> =
            ConflatedBroadcastChannel(initialState)
        val subject: Engine<ExampleState> = Engine(
            reducer,
            epic,
            testScope,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        subject.dispatch(ExampleAction.AddAction(2))
        subject.dispatch(ExampleAction.AddAction(2))

        // Execution
        val subscription = stateChanges.openSubscription()
        val listeningJob = launch {
            subscription.consumeEach {
                results.add(it)
            }
        }
        testScope.cancel()

        // Post-conditions
        assertFails {
            subject.dispatch(ExampleAction.AddAction(2))
        }
        assertEquals(1, results.size)
        listeningJob.cancel()
    }

    @Test
    fun `Epic runs post Reducer and receives reduced state`() = runBlockingTest {
        // Pre-conditions
        val results = ArrayList<ExampleState>()
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> =
            ConflatedBroadcastChannel(initialState)
        val testEpic = mockk<Epic<ExampleState>>()
        val stateSlot = slot<ExampleState>()
        val actionSlot = slot<Action>()
        val subject: Engine<ExampleState> = Engine(
            reducer,
            testEpic,
            testScope,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        val listeningJob = launch {
            stateChanges.openSubscription().consumeEach {
                results.add(it)
            }
        }
        every { testEpic.map(capture(actionSlot), capture(stateSlot)) } answers {
            emptyFlow()
        }

        // Execution
        subject.dispatch(ExampleAction.AddAction(2))

        // Post-conditions
        assertEquals(ExampleAction.AddAction(2), actionSlot.captured)
        assertEquals(ExampleState(2), stateSlot.captured)
        assertEquals(2, results.size)
        assertEquals(initialState, results[0])
        assertEquals(ExampleState(2), results[1])
        listeningJob.cancel()
    }

    @Test
    fun `Epic maps action to action`() = runBlockingTest {
        // Pre-conditions
        val initialState = ExampleState(0)
        val results = ArrayList<ExampleState>()
        val stateChanges: ConflatedBroadcastChannel<ExampleState> =
            ConflatedBroadcastChannel(initialState)
        val testEpic =
            Epic<ExampleState> { action, _ ->
                val actions = ArrayList<Action>()
                if (action is ExampleAction.AddAction) {
                    actions.add(ExampleAction.SubtractAction(4))
                }
                actions.asFlow()
            }
        val subject: Engine<ExampleState> = Engine(
            reducer,
            testEpic,
            testScope,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )
        val listeningJob = launch {
            stateChanges.openSubscription().consumeEach {
                results.add(it)
            }
        }

        // Execution
        subject.dispatch(ExampleAction.AddAction(2))

        // Post-conditions
        assertEquals(3, results.size)
        assertEquals(initialState, results[0])
        assertEquals(ExampleState(2), results[1])
        assertEquals(ExampleState(-2), results[2])
        listeningJob.cancel()
    }

    @Test
    fun `Exception thrown in Reducer can be handled`() = runBlockingTest {
        // Pre-conditions
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> =
            ConflatedBroadcastChannel(initialState)
        val exceptionThrowingReducer: Reducer<ExampleState> = Reducer { _, _ ->
            throw RuntimeException()
        }
        val handler = TestCoroutineExceptionHandler()
        val subject: Engine<ExampleState> = Engine(
            exceptionThrowingReducer,
            epic,
            testScope + handler,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )

        // Execution
        assertFailsWith<RuntimeException> {
            subject.dispatch(ExampleAction.AddAction(2))
        }

        // Post-conditions
        assertEquals(1, handler.uncaughtExceptions.size)
        assertTrue { handler.uncaughtExceptions[0] is RuntimeException }
    }

    @Test
    fun `Exception thrown in Epic can be handled`() = runBlockingTest {
        // Pre-conditions
        val initialState = ExampleState(0)
        val stateChanges: ConflatedBroadcastChannel<ExampleState> =
            ConflatedBroadcastChannel(initialState)
        val reducer: Reducer<ExampleState> = Reducer { _, state ->
            state
        }
        val exceptionThrowingEpic = Epic<ExampleState> { _, _ ->
            throw RuntimeException()
        }
        val handler = TestCoroutineExceptionHandler()
        val subject: Engine<ExampleState> = Engine(
            reducer,
            exceptionThrowingEpic,
            testScope + handler,
            testMainDispatcher,
            testIoDispatcher,
            stateChanges,
        )

        // Execution
        subject.dispatch(ExampleAction.AddAction(2))

        // Pre-conditions
        assertEquals(1, handler.uncaughtExceptions.size)
        assertTrue { handler.uncaughtExceptions[0] is java.lang.RuntimeException }
    }

    data class ExampleState(
        val value: Int,
        val subState: ExampleSubState? = null
    ) : State

    data class ExampleSubState(
        val subValue: Int
    )

    sealed class ExampleAction : Action {
        data class AddAction(
            val value: Int
        ) : ExampleAction()

        data class SubtractAction(
            val value: Int
        ) : ExampleAction()
    }

    class ExampleReducer : Reducer<ExampleState> {
        override fun reduce(action: Action, state: ExampleState): ExampleState {
            return when (action) {
                is ExampleAction.AddAction -> {
                    state.copy(
                        value = state.value + action.value
                    )
                }
                is ExampleAction.SubtractAction -> {
                    state.copy(
                        value = state.value - action.value
                    )
                }
                else -> {
                    state
                }
            }
        }
    }

    class ExampleEpic : Epic<ExampleState> {
        override fun map(action: Action, state: ExampleState): Flow<Action> {
            return emptyFlow()
        }
    }
}
