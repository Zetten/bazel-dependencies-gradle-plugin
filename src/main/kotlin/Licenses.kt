package com.github.zetten.bazeldeps

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Serializable

object Licenses {
    private val logger: Logger = LoggerFactory.getLogger(Licenses::class.java)

    fun getMostRestrictiveLicense(licenses: List<LicenseData>): String {
        return licenses.map { guessLicenseType(it) }
                .map { LicenseType.valueOf(it.toUpperCase()) }
                .sorted().first() // enum ordinal represents license restrictiveness
                .toString().toLowerCase()
    }

    // Ref: https://bazel.build/versions/master/docs/be/functions.html#licenses_args
    internal enum class LicenseType {
        BY_EXCEPTION_ONLY,
        RESTRICTED,
        RESTRICTED_IF_STATICALLY_LINKED,
        RECIPROCAL,
        NOTICE,
        PERMISSIVE,
        UNENCUMBERED,
        NONE
    }

    // Implementation from  https://github.com/jart/bazel/blob/ef0328910fd61a7e77f4c56443d87365eeac7174/tools/build_defs/repo/maven_config_generator/index.html
    private fun guessLicenseType(license: LicenseData): String {
        if (license.override != null) {
            return license.override
        } else {
            license.name!!
            license.url!!
        }
        if (license.url.indexOf("gnu.org/licenses/agpl") != -1 ||
                license.url.indexOf("wtfpl.net") != -1 ||
                license.name.indexOf("WTFPL") != -1 ||
                license.name.indexOf("AGPL") != -1 ||
                license.name.indexOf("Affero") != -1 ||
                license.name.indexOf("affero") != -1 ||
                license.name.indexOf("Fuck") != -1 ||
                license.name.indexOf("fuck") != -1) {
            return "by_exception_only"
        }
        if (license.url.indexOf("apache.org/licenses/LICENSE-2.0") != -1 ||
                license.url.indexOf("opensource.org/licenses/mit") != -1 ||
                license.url.indexOf("opensource.org/licenses/bsd") != -1 ||
                license.url.indexOf("opensource.org/licenses/BSD") != -1 ||
                license.url.indexOf("opensource.org/licenses/ISC") != -1 ||
                license.url.indexOf("opensource.org/licenses/zpl") != -1 ||
                license.url.indexOf("opensource.org/licenses/zlib") != -1 ||
                license.url.indexOf("unicode.org/copyright") != -1 ||
                license.url.indexOf("json.org/license") != -1 ||
                license.url.indexOf("gwtproject.org/terms") != -1 ||
                license.url.indexOf("golang.org/LICENSE") != -1) {
            return "notice"
        }
        if (license.url.indexOf("www.gnu.org") != -1 ||
                license.url.indexOf("www.oracle.com/technetwork/java/javase/terms/license/index.html") != -1 ||
                license.url.indexOf("opensource.org/licenses/sleepycat.php") != -1 ||
                license.url.indexOf("opensource.org/licenses/osl") != -1 ||
                license.url.indexOf("opensource.org/licenses/qtpl") != -1 ||
                license.url.indexOf("opensource.org/licenses/sleepycat") != -1 ||
                license.url.indexOf("cr.yp.to/qmail") != -1 ||
                license.url.indexOf("MPL/NPL/") != -1) {
            return "restricted"
        }
        if (license.url.indexOf("opensource.org/licenses/cpl") != -1 ||
                license.url.indexOf("opensource.org/licenses/eclipse") != -1 ||
                license.url.indexOf("opensource.org/licenses/apsl") != -1 ||
                license.url.indexOf("opensource.org/licenses/ibmpl") != -1 ||
                license.url.indexOf("eclipse.org/org/documents/epl") != -1 ||
                license.url.indexOf("eclipse.org/legal/epl") != -1) {
            return "reciprocal"
        }
        if (license.url.indexOf("creativecommons.org/licenses/publicdomain") != -1 ||
                license.url.indexOf("creativecommons.org/publicdomain") != -1 ||
                license.url.indexOf("unlicense.org") != -1) {
            return "unencumbered"
        }
        if (license.name == "gpl" ||
                license.name == "lgpl" ||
                license.name.indexOf("GPL") != -1 ||
                license.name.indexOf("BCL") != -1 ||
                license.name.indexOf("Oracle Binary Code License") != -1 ||
                license.name.indexOf("Attribution-ShareAlike") != -1 ||
                license.name.indexOf("CC BY-SA") != -1 ||
                license.name.indexOf("Attribution-NoDerivs") != -1 ||
                license.name.indexOf("CC BY-ND") != -1 ||
                license.name.indexOf("Sleepycat") != -1) {
            return "restricted"
        }
        if (license.name.indexOf("MPL") != -1 ||
                license.name.indexOf("Mozilla Public License") != -1 ||
                license.name.indexOf("Common Public License") != -1 ||
                license.name.indexOf("CDDL") != -1 ||
                license.name.indexOf("Common Development and Distribution License") != -1 ||
                license.name.indexOf("EPL") != -1 ||
                license.name.indexOf("Eclipse Public License") != -1 ||
                license.name.indexOf("APSL") != -1 ||
                license.name.indexOf("Apple Public Source License") != -1) {
            return "reciprocal"
        }
        if (license.name.indexOf("BSD") != -1 ||
                license.name.indexOf("MIT") != -1 ||
                license.name.indexOf("X11") != -1 ||
                license.name.indexOf("Apache") != -1 ||
                license.name.indexOf("Artistic") != -1 ||
                license.name.indexOf("ISC") != -1 ||
                license.name.indexOf("ICU") != -1 ||
                license.name.indexOf("JSON License") != -1 ||
                license.name.indexOf("Bouncy Castle Licence") != -1) {
            return "notice"
        }
        if (license.name.indexOf("Beerware") != -1 ||
                license.name.indexOf("beerware") != -1 ||
                license.name.indexOf("Google App Engine Terms of Service") != -1) {
            return "permissive"
        }
        if (license.name == "Public Domain" ||
                license.name == "CC0") {
            return "unencumbered"  // e.g. aopalliance
        }
        logger.info("No Bazel-registered license detected for ${license}")
        return "none"
    }

}

data class LicenseData(val name: String?, val url: String?, val override: String?) : Serializable