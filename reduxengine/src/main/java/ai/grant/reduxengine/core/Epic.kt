package ai.grant.reduxengine.core

import kotlinx.coroutines.flow.Flow

fun interface Epic<S : State> {
    fun map(action: Action, state: S): Flow<Action>
}

