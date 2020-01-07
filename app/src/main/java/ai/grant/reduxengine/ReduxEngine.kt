package ai.grant.reduxengine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.launch

typealias Epic = (Action, State) -> ArrayList<Action>

typealias Reducer = (State, Action) -> State

interface State

interface Action

object NoOpAction : Action

interface Store {
    val stateChanges: ConflatedBroadcastChannel<State>

    fun dispatch(action: Action)

    fun addReducer(reducer: Reducer)

    fun addEpic(epic: Epic)
}

class ReduxEngine(private val firstState: State) : Store {

    private val reducers = ArrayList<Reducer>()

    private val epics = ArrayList<Epic>()

    override val stateChanges: ConflatedBroadcastChannel<State> =
        ConflatedBroadcastChannel(firstState)

    override fun dispatch(action: Action) {
        var currentState: State? = null
        MainScope().launch {
            val updatedState = reducers.foldRight(firstState) { reducer, state ->
                reducer(state, action)
            }
            currentState = updatedState
            stateChanges.send(updatedState)
        }.invokeOnCompletion { throwable ->
            if (throwable == null) {
                CoroutineScope(Dispatchers.IO).launch {
                    if (action !is NoOpAction) {
                        epics.forEach { epic ->
                            epic(action, currentState!!).forEach { action ->
                                dispatch(action)
                            }
                        }
                    }
                }
            } else {
                throw throwable
            }
        }
    }

    override fun addReducer(reducer: Reducer) {
        reducers.add(reducer)
    }

    override fun addEpic(epic: Epic) {
        epics.add(epic)
    }
}
