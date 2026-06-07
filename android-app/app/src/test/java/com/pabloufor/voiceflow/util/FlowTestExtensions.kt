package com.pabloufor.voiceflow.util

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow

suspend fun <T> Flow<T>.testValues(
    validate: suspend TurbineTestContext<T>.() -> Unit,
) = test(validate = validate)
