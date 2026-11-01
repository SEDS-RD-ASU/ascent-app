package com.example.bluefalconcomposemultiplatform.checklist

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

actual fun currentEpochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

// The short time style follows the phone's region and its 24-Hour Time setting
actual fun formatClockTime(epochMillis: Long): String {
    val formatter = NSDateFormatter()
    formatter.dateStyle = NSDateFormatterNoStyle
    formatter.timeStyle = NSDateFormatterShortStyle
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))
}
