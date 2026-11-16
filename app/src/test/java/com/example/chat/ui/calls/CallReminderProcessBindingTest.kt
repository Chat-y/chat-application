package com.example.chat.ui.calls

import org.junit.Assert.*
import org.junit.Test

class CallReminderProcessBindingTest {
    @Test fun exactReferenceOnlyEvenWhenOwnersCompareEqual() {
        val binding = CallReminderProcessBinding()
        val first = String(charArrayOf('a'))
        val second = String(charArrayOf('a'))
        val nonce = binding.bind(first)
        assertTrue(binding.matches(first, nonce))
        assertFalse(binding.matches(second, nonce))
        assertFalse(binding.matches(first, null))
    }
    @Test fun newProcessCannotRecreateBindingAndNewSessionInvalidatesOld() {
        val binding = CallReminderProcessBinding()
        val first = Any(); val second = Any()
        val nonce = binding.bind(first)
        assertFalse(CallReminderProcessBinding().matches(first, nonce))
        binding.bind(second)
        assertFalse(binding.matches(first, nonce))
    }
}
