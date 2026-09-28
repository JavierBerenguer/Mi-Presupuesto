package com.mipatrimonio.app.ui.importer

import com.mipatrimonio.app.data.importer.ImportedMovement

internal fun canConvertToMovement(row: ImportedMovement): Boolean =
    row.amountCents != 0L && !row.rawType.equals("PRIVATE_MARKET_BUY", ignoreCase = true)
