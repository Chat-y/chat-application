package com.example.chat.ui.calls

/** Local matching only. A T9 name match never authorizes starting a call. */
internal fun matchesDialerContact(query: String, displayName: String, phone: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    if (displayName.lowercase().contains(q) || phone.lowercase().contains(q)) return true
    val digits = q.filter(Char::isDigit)
    if (digits.isNotEmpty() && phone.filter(Char::isDigit).contains(digits)) return true
    // Only digit queries use T9. Symbols alone must not match every contact.
    if (!q.all { it in '0'..'9' }) return false
    val nameDigits = buildString {
        for (letter in displayName.lowercase()) {
            val digit = when (letter) {
                in 'a'..'c' -> '2'
                in 'd'..'f' -> '3'
                in 'g'..'i' -> '4'
                in 'j'..'l' -> '5'
                in 'm'..'o' -> '6'
                in 'p'..'s' -> '7'
                in 't'..'v' -> '8'
                in 'w'..'z' -> '9'
                else -> null
            }
            if (digit != null) append(digit)
        }
    }
    return nameDigits.contains(q)
}

/** CALLS-R1-30: the T9/digit form of a contact, computable once per contact instead of per keystroke. */
internal fun dialerContactSearchBlob(displayName: String, phone: String): String {
    val nameDigits = buildString {
        for (letter in displayName.lowercase()) {
            val digit = when (letter) {
                in 'a'..'c' -> '2'
                in 'd'..'f' -> '3'
                in 'g'..'i' -> '4'
                in 'j'..'l' -> '5'
                in 'm'..'o' -> '6'
                in 'p'..'s' -> '7'
                in 't'..'v' -> '8'
                in 'w'..'z' -> '9'
                else -> null
            }
            if (digit != null) append(digit)
        }
    }
    return displayName.lowercase() + ' ' + phone.lowercase() + ' ' + phone.filter(Char::isDigit) + ' ' + nameDigits
}

/** Local matching only, against a precomputed [dialerContactSearchBlob]. A T9 match never authorizes a call. */
internal fun matchesDialerBlob(query: String, blob: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    val digits = q.filter(Char::isDigit)
    // Substring covers name and phone; digit-only queries also match the precomputed T9 form.
    if (blob.contains(q)) return true
    if (digits.isNotEmpty() && blob.contains(digits)) return true
    return false
}
