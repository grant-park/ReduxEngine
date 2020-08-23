package ai.grant.reduxengine

import ai.grant.reduxenginesampleproject.reduxengine.CompositeEpic
import ai.grant.reduxenginesampleproject.reduxengine.CompositeReducer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

interface State

interface Action

fun interface Epic<S : State> {
    fun map(action: Action, state: S): Flow<Action>
}

fun interface Reducer<S : State> {
    fun reduce(action: Action, state: S): S
}

fun <S> ReceiveChannel<S>.addTo(collection: MutableCollection<ReceiveChannel<S>>) {
    collection.add(this)
}

@ExperimentalCoroutinesApi
class ReduxEngine<S : State> internal constructor(
    private val reducer: Reducer<S>,
    private val epic: Epic<S>,
    private val job: Job,
    private val mainDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateChanges: ConflatedBroadcastChannel<S>
) : CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = job + mainDispatcher

    internal fun <T> listen(selector: (S) -> T, action: (T) -> Unit): ReceiveChannel<S> {
        val subscription = stateChanges.openSubscription()
        launch {
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

    companion object INSTANCE {
        private var ENGINE: ReduxEngine<out State>? = null

        fun <S : State> initialize(
            reducers: List<Reducer<S>>,
            epics: List<Epic<S>>,
            initialState: S,
            job: Job = Job(),
            mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        ) {
            ENGINE = ReduxEngine(
                CompositeReducer(reducers),
                CompositeEpic(epics),
                job,
                mainDispatcher,
                ioDispatcher,
                ConflatedBroadcastChannel(initialState)
            )
        }

        fun <S : State, T> listen(selector: (S) -> T, action: (T) -> Unit): ReceiveChannel<S> {
            return (ENGINE as ReduxEngine<S>).listen(selector, action)
        }

        fun <S : State> listen(action: (S) -> Unit): ReceiveChannel<S> {
            return (ENGINE as ReduxEngine<S>).listen(action)
        }

        fun dispatch(action: Action) {
            ENGINE!!.dispatch(action)
        }

        fun <S : State> stateChanges(): ConflatedBroadcastChannel<S> {
            return (ENGINE as ReduxEngine<S>).stateChanges
        }
    }
}
