package com.safesteps.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

class AppStringResolver(
    private val context: Context
) {
    fun string(@StringRes resId: Int, vararg formatArgs: Any): String {
        return context.getString(resId, *formatArgs)
    }

    fun plural(@PluralsRes resId: Int, quantity: Int, vararg formatArgs: Any): String {
        return context.resources.getQuantityString(resId, quantity, *formatArgs)
    }
}

val LocalAppStringResolver = staticCompositionLocalOf<AppStringResolver> {
    error("AppStringResolver not provided")
}

fun Context.localizedContext(language: AppLanguage): Context {
    val configuration = Configuration(resources.configuration)
    val locale = Locale.forLanguageTag(language.languageTag)
    configuration.setLocale(locale)
    configuration.setLayoutDirection(locale)
    return createConfigurationContext(configuration)
}

@Composable
fun ProvideLocalizedStrings(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, language) {
        baseContext.localizedContext(language)
    }
    val resolver = remember(localizedContext) {
        AppStringResolver(localizedContext)
    }

    CompositionLocalProvider(LocalAppStringResolver provides resolver) {
        content()
    }
}

@Composable
fun appString(
    @StringRes resId: Int,
    vararg formatArgs: Any
): String {
    val resolver = LocalAppStringResolver.current
    return remember(resolver, resId, *formatArgs) {
        resolver.string(resId, *formatArgs)
    }
}

@Composable
fun appPlural(
    @PluralsRes resId: Int,
    quantity: Int,
    vararg formatArgs: Any
): String {
    val resolver = LocalAppStringResolver.current
    return remember(resolver, resId, quantity, *formatArgs) {
        resolver.plural(resId, quantity, *formatArgs)
    }
}
