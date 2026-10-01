package app.nightbrief.astro.sgp4

import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * TEME state from SGP4: position in kilometres and velocity in kilometres per second,
 * in the true-equator mean-equinox frame.
 */
data class Tem(
    val xKm: Double,
    val yKm: Double,
    val zKm: Double,
    val vxKmPerSec: Double,
    val vyKmPerSec: Double,
    val vzKmPerSec: Double,
)

/** SGP4 could not produce a state (decayed elements, or a mean element out of range). */
class Sgp4Exception(message: String) : IllegalArgumentException(message)

/**
 * SGP4/SDP4 propagator, Vallado's revision of Spacetrack Report #3.
 *
 * Gravity is WGS-72 as used by the published AFSPC verification
 * (`mu = 398600.8 km³/s²`, `radius = 6378.135 km`, and the companion `xke`, `tumin`,
 * `j2`, `j3`, `j4`). The operation mode is AFSPC (`'a'`), which is the mode that
 * reproduces the Vallado companion vectors. Near-Earth and deep-space (period ≥ 225 min,
 * including half-day and one-day resonance) are both propagated.
 */
object Sgp4 {
    fun propagate(line1: String, line2: String, at: Instant): Tem = model(line1, line2).propagate(at)

    internal fun model(line1: String, line2: String): Sgp4Model = Sgp4Model(Tle.parse(line1, line2))
}

internal class Sgp4Model(val tle: Tle) {
    private val sat = Sat().also { sgp4init(it, tle) }

    fun propagate(at: Instant): Tem {
        val minutes = Duration.between(tle.epoch, at).toNanos() / 60e9
        return sgp4(sat, minutes)
    }
}

/** GMST in radians, Vallado eq. 3-45, used to rotate TEME into ECEF (no polar motion). */
internal fun greenwichMeanSiderealRad(at: Instant): Double {
    val tut1 = (julianDate(at) - 2451545.0) / 36525.0
    val seconds = -6.2e-6 * tut1 * tut1 * tut1 +
        0.093104 * tut1 * tut1 +
        (876600.0 * 3600.0 + 8640184.812866) * tut1 +
        67310.54841
    val gst = pmod(seconds * (PI / 180.0) / 240.0, TWOPI)
    return if (gst < 0.0) gst + TWOPI else gst
}

private const val TWOPI = 2.0 * PI
private const val DEG2RAD = PI / 180.0
private const val OPSMODE = 'a'

// WGS-72 constants from Vallado's getgravconst (not WGS-84).
private const val MU = 398600.8
private const val RADIUS_EARTH_KM = 6378.135
private const val J2 = 0.001082616
private const val J3 = -0.00000253881
private const val J4 = -0.00000165597
private val XKE = 60.0 / sqrt(RADIUS_EARTH_KM * RADIUS_EARTH_KM * RADIUS_EARTH_KM / MU)
private val J3OJ2 = J3 / J2

private class Sat {
    var method: Char = 'n'
    var isimp: Int = 0
    var irez: Int = 0
    var t: Double = 0.0
    var mo: Double = 0.0
    var mdot: Double = 0.0
    var argpo: Double = 0.0
    var argpdot: Double = 0.0
    var nodeo: Double = 0.0
    var nodedot: Double = 0.0
    var nodecf: Double = 0.0
    var cc1: Double = 0.0
    var bstar: Double = 0.0
    var cc4: Double = 0.0
    var cc5: Double = 0.0
    var t2cof: Double = 0.0
    var t3cof: Double = 0.0
    var t4cof: Double = 0.0
    var t5cof: Double = 0.0
    var omgcof: Double = 0.0
    var eta: Double = 0.0
    var xmcof: Double = 0.0
    var delmo: Double = 0.0
    var d2: Double = 0.0
    var d3: Double = 0.0
    var d4: Double = 0.0
    var sinmao: Double = 0.0
    var noUnkozai: Double = 0.0
    var ecco: Double = 0.0
    var inclo: Double = 0.0
    var con41: Double = 0.0
    var x1mth2: Double = 0.0
    var x7thm1: Double = 0.0
    var aycof: Double = 0.0
    var xlcof: Double = 0.0
    var gsto: Double = 0.0
    var xfact: Double = 0.0
    var xlamo: Double = 0.0
    var atime: Double = 0.0
    var xli: Double = 0.0
    var xni: Double = 0.0
    var d2201: Double = 0.0
    var d2211: Double = 0.0
    var d3210: Double = 0.0
    var d3222: Double = 0.0
    var d4410: Double = 0.0
    var d4422: Double = 0.0
    var d5220: Double = 0.0
    var d5232: Double = 0.0
    var d5421: Double = 0.0
    var d5433: Double = 0.0
    var dedt: Double = 0.0
    var didt: Double = 0.0
    var dmdt: Double = 0.0
    var dnodt: Double = 0.0
    var domdt: Double = 0.0
    var del1: Double = 0.0
    var del2: Double = 0.0
    var del3: Double = 0.0
    var e3: Double = 0.0
    var ee2: Double = 0.0
    var peo: Double = 0.0
    var pgho: Double = 0.0
    var pho: Double = 0.0
    var pinco: Double = 0.0
    var plo: Double = 0.0
    var se2: Double = 0.0
    var se3: Double = 0.0
    var sgh2: Double = 0.0
    var sgh3: Double = 0.0
    var sgh4: Double = 0.0
    var sh2: Double = 0.0
    var sh3: Double = 0.0
    var si2: Double = 0.0
    var si3: Double = 0.0
    var sl2: Double = 0.0
    var sl3: Double = 0.0
    var sl4: Double = 0.0
    var xgh2: Double = 0.0
    var xgh3: Double = 0.0
    var xgh4: Double = 0.0
    var xh2: Double = 0.0
    var xh3: Double = 0.0
    var xi2: Double = 0.0
    var xi3: Double = 0.0
    var xl2: Double = 0.0
    var xl3: Double = 0.0
    var xl4: Double = 0.0
    var zmol: Double = 0.0
    var zmos: Double = 0.0
}

private class Mean(val ep: Double, val inclp: Double, val nodep: Double, val argpp: Double, val mp: Double)

private class DeepPeriodic(
    val sinim: Double,
    val cosim: Double,
    val em: Double,
    val emsq: Double,
    val s1: Double,
    val s2: Double,
    val s3: Double,
    val s4: Double,
    val s5: Double,
    val ss1: Double,
    val ss2: Double,
    val ss3: Double,
    val ss4: Double,
    val ss5: Double,
    val sz1: Double,
    val sz3: Double,
    val sz11: Double,
    val sz13: Double,
    val sz21: Double,
    val sz23: Double,
    val sz31: Double,
    val sz33: Double,
    val z1: Double,
    val z3: Double,
    val z11: Double,
    val z13: Double,
    val z21: Double,
    val z23: Double,
    val z31: Double,
    val z33: Double,
)

private fun sgp4init(sat: Sat, tle: Tle) {
    sat.bstar = tle.bstar
    sat.ecco = tle.ecco
    sat.argpo = tle.argpoRad
    sat.inclo = tle.incloRad
    sat.mo = tle.moRad
    sat.nodeo = tle.nodeoRad
    sat.t = 0.0

    val init = initl(tle.ecco, tle.epochDaysFrom1950, tle.incloRad, tle.noKozaiRadPerMin)
    sat.noUnkozai = init.noUnkozai
    sat.con41 = init.con41
    sat.gsto = init.gsto

    val x2o3 = 2.0 / 3.0
    val ss = 78.0 / RADIUS_EARTH_KM + 1.0
    val qzms2tTemp = (120.0 - 78.0) / RADIUS_EARTH_KM
    val qzms2t = qzms2tTemp.pow(4)
    if (init.omeosq >= 0.0 || sat.noUnkozai >= 0.0) {
        sat.isimp = 0
        if (init.rp < 220.0 / RADIUS_EARTH_KM + 1.0) sat.isimp = 1
        var sfour = ss
        var qzms24 = qzms2t
        val perige = (init.rp - 1.0) * RADIUS_EARTH_KM
        if (perige < 156.0) {
            sfour = if (perige < 98.0) 20.0 else perige - 78.0
            val qzms24Temp = (120.0 - sfour) / RADIUS_EARTH_KM
            qzms24 = qzms24Temp.pow(4)
            sfour = sfour / RADIUS_EARTH_KM + 1.0
        }
        val pinvsq = 1.0 / init.posq
        val tsi = 1.0 / (init.ao - sfour)
        sat.eta = init.ao * sat.ecco * tsi
        val etasq = sat.eta * sat.eta
        val eeta = sat.ecco * sat.eta
        val psisq = abs(1.0 - etasq)
        val coef = qzms24 * tsi.pow(4)
        val coef1 = coef / psisq.pow(3.5)
        val cc2 = coef1 * sat.noUnkozai * (
            init.ao * (1.0 + 1.5 * etasq + eeta * (4.0 + etasq)) +
                0.375 * J2 * tsi / psisq * sat.con41 * (8.0 + 3.0 * etasq * (8.0 + etasq))
            )
        sat.cc1 = sat.bstar * cc2
        var cc3 = 0.0
        if (sat.ecco > 1.0e-4) {
            cc3 = -2.0 * coef * tsi * J3OJ2 * sat.noUnkozai * init.sinio / sat.ecco
        }
        sat.x1mth2 = 1.0 - init.cosio2
        sat.cc4 = 2.0 * sat.noUnkozai * coef1 * init.ao * init.omeosq * (
            sat.eta * (2.0 + 0.5 * etasq) + sat.ecco * (0.5 + 2.0 * etasq) -
                J2 * tsi / (init.ao * psisq) * (
                    -3.0 * sat.con41 * (1.0 - 2.0 * eeta + etasq * (1.5 - 0.5 * eeta)) +
                        0.75 * sat.x1mth2 * (2.0 * etasq - eeta * (1.0 + etasq)) * cos(2.0 * sat.argpo)
                    )
            )
        sat.cc5 = 2.0 * coef1 * init.ao * init.omeosq * (1.0 + 2.75 * (etasq + eeta) + eeta * etasq)
        val cosio4 = init.cosio2 * init.cosio2
        val temp1 = 1.5 * J2 * pinvsq * sat.noUnkozai
        val temp2 = 0.5 * temp1 * J2 * pinvsq
        val temp3 = -0.46875 * J4 * pinvsq * pinvsq * sat.noUnkozai
        sat.mdot = sat.noUnkozai + 0.5 * temp1 * init.rteosq * sat.con41 +
            0.0625 * temp2 * init.rteosq * (13.0 - 78.0 * init.cosio2 + 137.0 * cosio4)
        sat.argpdot = -0.5 * temp1 * init.con42 +
            0.0625 * temp2 * (7.0 - 114.0 * init.cosio2 + 395.0 * cosio4) +
            temp3 * (3.0 - 36.0 * init.cosio2 + 49.0 * cosio4)
        val xhdot1 = -temp1 * init.cosio
        sat.nodedot = xhdot1 + (0.5 * temp2 * (4.0 - 19.0 * init.cosio2) + 2.0 * temp3 * (3.0 - 7.0 * init.cosio2)) * init.cosio
        val xpidot = sat.argpdot + sat.nodedot
        sat.omgcof = sat.bstar * cc3 * cos(sat.argpo)
        sat.xmcof = if (sat.ecco > 1.0e-4) -x2o3 * coef * sat.bstar / eeta else 0.0
        sat.nodecf = 3.5 * init.omeosq * xhdot1 * sat.cc1
        sat.t2cof = 1.5 * sat.cc1
        val temp4 = 1.5e-12
        sat.xlcof = if (abs(init.cosio + 1.0) > 1.5e-12) {
            -0.25 * J3OJ2 * init.sinio * (3.0 + 5.0 * init.cosio) / (1.0 + init.cosio)
        } else {
            -0.25 * J3OJ2 * init.sinio * (3.0 + 5.0 * init.cosio) / temp4
        }
        sat.aycof = -0.5 * J3OJ2 * init.sinio
        val delmotemp = 1.0 + sat.eta * cos(sat.mo)
        sat.delmo = delmotemp * delmotemp * delmotemp
        sat.sinmao = sin(sat.mo)
        sat.x7thm1 = 7.0 * init.cosio2 - 1.0

        if (TWOPI / sat.noUnkozai >= 225.0) {
            sat.method = 'd'
            sat.isimp = 1
            val tc = 0.0
            val deep = dscom(sat, tle.epochDaysFrom1950, sat.ecco, sat.argpo, tc, sat.inclo, sat.nodeo, sat.noUnkozai)
            // Epoch call: periodics are evaluated but not applied (init == 'y').
            dpper(sat, init = true, ep = sat.ecco, inclp = sat.inclo, nodep = sat.nodeo, argpp = sat.argpo, mp = sat.mo)
            dsinit(
                sat,
                cosim = deep.cosim,
                emsq = deep.emsq,
                argpo = sat.argpo,
                s1 = deep.s1, s2 = deep.s2, s3 = deep.s3, s4 = deep.s4, s5 = deep.s5,
                sinim = deep.sinim,
                ss1 = deep.ss1, ss2 = deep.ss2, ss3 = deep.ss3, ss4 = deep.ss4, ss5 = deep.ss5,
                sz1 = deep.sz1, sz3 = deep.sz3, sz11 = deep.sz11, sz13 = deep.sz13,
                sz21 = deep.sz21, sz23 = deep.sz23, sz31 = deep.sz31, sz33 = deep.sz33,
                t = sat.t, tc = tc, gsto = sat.gsto, mo = sat.mo, mdot = sat.mdot,
                no = sat.noUnkozai, nodeo = sat.nodeo, nodedot = sat.nodedot, xpidot = xpidot,
                z1 = deep.z1, z3 = deep.z3, z11 = deep.z11, z13 = deep.z13,
                z21 = deep.z21, z23 = deep.z23, z31 = deep.z31, z33 = deep.z33,
                ecco = sat.ecco, eccsq = init.eccsq, em = deep.em,
            )
        }
        if (sat.isimp != 1) {
            val cc1sq = sat.cc1 * sat.cc1
            sat.d2 = 4.0 * init.ao * tsi * cc1sq
            val temp = sat.d2 * tsi * sat.cc1 / 3.0
            sat.d3 = (17.0 * init.ao + sfour) * temp
            sat.d4 = 0.5 * temp * init.ao * tsi * (221.0 * init.ao + 31.0 * sfour) * sat.cc1
            sat.t3cof = sat.d2 + 2.0 * cc1sq
            sat.t4cof = 0.25 * (3.0 * sat.d3 + sat.cc1 * (12.0 * sat.d2 + 10.0 * cc1sq))
            sat.t5cof = 0.2 * (
                3.0 * sat.d4 + 12.0 * sat.cc1 * sat.d3 + 6.0 * sat.d2 * sat.d2 +
                    15.0 * cc1sq * (2.0 * sat.d2 + cc1sq)
                )
        }
    }
    sgp4(sat, 0.0)
}

private class Initl(
    val noUnkozai: Double,
    val ao: Double,
    val con41: Double,
    val con42: Double,
    val cosio: Double,
    val cosio2: Double,
    val eccsq: Double,
    val omeosq: Double,
    val posq: Double,
    val rp: Double,
    val rteosq: Double,
    val sinio: Double,
    val gsto: Double,
)

private fun initl(ecco: Double, epoch: Double, inclo: Double, noKozai: Double): Initl {
    val x2o3 = 2.0 / 3.0
    val eccsq = ecco * ecco
    val omeosq = 1.0 - eccsq
    val rteosq = sqrt(omeosq)
    val cosio = cos(inclo)
    val cosio2 = cosio * cosio
    val ak = (XKE / noKozai).pow(x2o3)
    val d1 = 0.75 * J2 * (3.0 * cosio2 - 1.0) / (rteosq * omeosq)
    var del = d1 / (ak * ak)
    val adel = ak * (1.0 - del * del - del * (1.0 / 3.0 + 134.0 * del * del / 81.0))
    del = d1 / (adel * adel)
    val no = noKozai / (1.0 + del)
    val ao = (XKE / no).pow(x2o3)
    val sinio = sin(inclo)
    val po = ao * omeosq
    val con42 = 1.0 - 5.0 * cosio2
    val con41 = -con42 - cosio2 - cosio2
    val posq = po * po
    val rp = ao * (1.0 - ecco)
    val gsto = if (OPSMODE == 'a') {
        val ts70 = epoch - 7305.0
        val ds70 = floor(ts70 + 1.0e-8)
        val tfrac = ts70 - ds70
        val c1 = 1.72027916940703639e-2
        val thgr70 = 1.7321343856509374
        val fk5r = 5.07551419432269442e-15
        val c1p2p = c1 + TWOPI
        var gst = pmod(thgr70 + c1 * ds70 + c1p2p * tfrac + ts70 * ts70 * fk5r, TWOPI)
        if (gst < 0.0) gst += TWOPI
        gst
    } else {
        var gst = gstime(epoch + 2433281.5)
        if (gst < 0.0) gst += TWOPI
        gst
    }
    return Initl(no, ao, con41, con42, cosio, cosio2, eccsq, omeosq, posq, rp, rteosq, sinio, gsto)
}

private fun gstime(jdUt1: Double): Double {
    val tut1 = (jdUt1 - 2451545.0) / 36525.0
    val seconds = -6.2e-6 * tut1 * tut1 * tut1 + 0.093104 * tut1 * tut1 +
        (876600.0 * 3600.0 + 8640184.812866) * tut1 + 67310.54841
    return pmod(seconds * DEG2RAD / 240.0, TWOPI)
}

private fun dscom(
    sat: Sat,
    epoch: Double,
    ep: Double,
    argpp: Double,
    tc: Double,
    inclp: Double,
    nodep: Double,
    np: Double,
): DeepPeriodic {
    val zes = 0.01675
    val zel = 0.05490
    val c1ss = 2.9864797e-6
    val c1l = 4.7968065e-7
    val zsinis = 0.39785416
    val zcosis = 0.91744867
    val zcosgs = 0.1945905
    val zsings = -0.98088458

    val em = ep
    val snodm = sin(nodep)
    val cnodm = cos(nodep)
    val sinomm = sin(argpp)
    val cosomm = cos(argpp)
    val sinim = sin(inclp)
    val cosim = cos(inclp)
    val emsq = em * em
    val betasq = 1.0 - emsq
    val rtemsq = sqrt(betasq)

    sat.peo = 0.0
    sat.pinco = 0.0
    sat.plo = 0.0
    sat.pgho = 0.0
    sat.pho = 0.0
    val day = epoch + 18261.5 + tc / 1440.0
    val xnodce = pmod(4.5236020 - 9.2422029e-4 * day, TWOPI)
    val stem = sin(xnodce)
    val ctem = cos(xnodce)
    val zcosil = 0.91375164 - 0.03568096 * ctem
    val zsinil = sqrt(1.0 - zcosil * zcosil)
    val zsinhl = 0.089683511 * stem / zsinil
    val zcoshl = sqrt(1.0 - zsinhl * zsinhl)
    val gam = 5.8351514 + 0.0019443680 * day
    var zx = 0.39785416 * stem / zsinil
    val zy = zcoshl * ctem + 0.91744867 * zsinhl * stem
    zx = atan2(zx, zy)
    zx = gam + zx - xnodce
    val zcosgl = cos(zx)
    val zsingl = sin(zx)

    var zcosg = zcosgs
    var zsing = zsings
    var zcosi = zcosis
    var zsini = zsinis
    var zcosh = cnodm
    var zsinh = snodm
    var cc = c1ss
    val xnoi = 1.0 / np

    var s1 = 0.0
    var s2 = 0.0
    var s3 = 0.0
    var s4 = 0.0
    var s5 = 0.0
    var s6 = 0.0
    var s7 = 0.0
    var ss1 = 0.0
    var ss2 = 0.0
    var ss3 = 0.0
    var ss4 = 0.0
    var ss5 = 0.0
    var ss6 = 0.0
    var ss7 = 0.0
    var sz1 = 0.0
    var sz2 = 0.0
    var sz3 = 0.0
    var sz11 = 0.0
    var sz12 = 0.0
    var sz13 = 0.0
    var sz21 = 0.0
    var sz22 = 0.0
    var sz23 = 0.0
    var sz31 = 0.0
    var sz32 = 0.0
    var sz33 = 0.0
    var z1 = 0.0
    var z2 = 0.0
    var z3 = 0.0
    var z11 = 0.0
    var z12 = 0.0
    var z13 = 0.0
    var z21 = 0.0
    var z22 = 0.0
    var z23 = 0.0
    var z31 = 0.0
    var z32 = 0.0
    var z33 = 0.0

    for (lsflg in 1..2) {
        val a1 = zcosg * zcosh + zsing * zcosi * zsinh
        val a3 = -zsing * zcosh + zcosg * zcosi * zsinh
        val a7 = -zcosg * zsinh + zsing * zcosi * zcosh
        val a8 = zsing * zsini
        val a9 = zsing * zsinh + zcosg * zcosi * zcosh
        val a10 = zcosg * zsini
        val a2 = cosim * a7 + sinim * a8
        val a4 = cosim * a9 + sinim * a10
        val a5 = -sinim * a7 + cosim * a8
        val a6 = -sinim * a9 + cosim * a10
        val x1 = a1 * cosomm + a2 * sinomm
        val x2 = a3 * cosomm + a4 * sinomm
        val x3 = -a1 * sinomm + a2 * cosomm
        val x4 = -a3 * sinomm + a4 * cosomm
        val x5 = a5 * sinomm
        val x6 = a6 * sinomm
        val x7 = a5 * cosomm
        val x8 = a6 * cosomm
        z31 = 12.0 * x1 * x1 - 3.0 * x3 * x3
        z32 = 24.0 * x1 * x2 - 6.0 * x3 * x4
        z33 = 12.0 * x2 * x2 - 3.0 * x4 * x4
        z1 = 3.0 * (a1 * a1 + a2 * a2) + z31 * emsq
        z2 = 6.0 * (a1 * a3 + a2 * a4) + z32 * emsq
        z3 = 3.0 * (a3 * a3 + a4 * a4) + z33 * emsq
        z11 = -6.0 * a1 * a5 + emsq * (-24.0 * x1 * x7 - 6.0 * x3 * x5)
        z12 = -6.0 * (a1 * a6 + a3 * a5) + emsq * (-24.0 * (x2 * x7 + x1 * x8) - 6.0 * (x3 * x6 + x4 * x5))
        z13 = -6.0 * a3 * a6 + emsq * (-24.0 * x2 * x8 - 6.0 * x4 * x6)
        z21 = 6.0 * a2 * a5 + emsq * (24.0 * x1 * x5 - 6.0 * x3 * x7)
        z22 = 6.0 * (a4 * a5 + a2 * a6) + emsq * (24.0 * (x2 * x5 + x1 * x6) - 6.0 * (x4 * x7 + x3 * x8))
        z23 = 6.0 * a4 * a6 + emsq * (24.0 * x2 * x6 - 6.0 * x4 * x8)
        z1 += z1 + betasq * z31
        z2 += z2 + betasq * z32
        z3 += z3 + betasq * z33
        s3 = cc * xnoi
        s2 = -0.5 * s3 / rtemsq
        s4 = s3 * rtemsq
        s1 = -15.0 * em * s4
        s5 = x1 * x3 + x2 * x4
        s6 = x2 * x3 + x1 * x4
        s7 = x2 * x4 - x1 * x3
        if (lsflg == 1) {
            ss1 = s1; ss2 = s2; ss3 = s3; ss4 = s4; ss5 = s5; ss6 = s6; ss7 = s7
            sz1 = z1; sz2 = z2; sz3 = z3
            sz11 = z11; sz12 = z12; sz13 = z13
            sz21 = z21; sz22 = z22; sz23 = z23
            sz31 = z31; sz32 = z32; sz33 = z33
            zcosg = zcosgl
            zsing = zsingl
            zcosi = zcosil
            zsini = zsinil
            zcosh = zcoshl * cnodm + zsinhl * snodm
            zsinh = snodm * zcoshl - cnodm * zsinhl
            cc = c1l
        }
    }

    sat.zmol = pmod(4.7199672 + 0.22997150 * day - gam, TWOPI)
    sat.zmos = pmod(6.2565837 + 0.017201977 * day, TWOPI)
    sat.se2 = 2.0 * ss1 * ss6
    sat.se3 = 2.0 * ss1 * ss7
    sat.si2 = 2.0 * ss2 * sz12
    sat.si3 = 2.0 * ss2 * (sz13 - sz11)
    sat.sl2 = -2.0 * ss3 * sz2
    sat.sl3 = -2.0 * ss3 * (sz3 - sz1)
    sat.sl4 = -2.0 * ss3 * (-21.0 - 9.0 * emsq) * zes
    sat.sgh2 = 2.0 * ss4 * sz32
    sat.sgh3 = 2.0 * ss4 * (sz33 - sz31)
    sat.sgh4 = -18.0 * ss4 * zes
    sat.sh2 = -2.0 * ss2 * sz22
    sat.sh3 = -2.0 * ss2 * (sz23 - sz21)
    sat.ee2 = 2.0 * s1 * s6
    sat.e3 = 2.0 * s1 * s7
    sat.xi2 = 2.0 * s2 * z12
    sat.xi3 = 2.0 * s2 * (z13 - z11)
    sat.xl2 = -2.0 * s3 * z2
    sat.xl3 = -2.0 * s3 * (z3 - z1)
    sat.xl4 = -2.0 * s3 * (-21.0 - 9.0 * emsq) * zel
    sat.xgh2 = 2.0 * s4 * z32
    sat.xgh3 = 2.0 * s4 * (z33 - z31)
    sat.xgh4 = -18.0 * s4 * zel
    sat.xh2 = -2.0 * s2 * z22
    sat.xh3 = -2.0 * s2 * (z23 - z21)

    return DeepPeriodic(
        sinim, cosim, em, emsq,
        s1, s2, s3, s4, s5,
        ss1, ss2, ss3, ss4, ss5,
        sz1, sz3, sz11, sz13, sz21, sz23, sz31, sz33,
        z1, z3, z11, z13, z21, z23, z31, z33,
    )
}

private fun dsinit(
    sat: Sat,
    cosim: Double,
    emsq: Double,
    argpo: Double,
    s1: Double,
    s2: Double,
    s3: Double,
    s4: Double,
    s5: Double,
    sinim: Double,
    ss1: Double,
    ss2: Double,
    ss3: Double,
    ss4: Double,
    ss5: Double,
    sz1: Double,
    sz3: Double,
    sz11: Double,
    sz13: Double,
    sz21: Double,
    sz23: Double,
    sz31: Double,
    sz33: Double,
    t: Double,
    tc: Double,
    gsto: Double,
    mo: Double,
    mdot: Double,
    no: Double,
    nodeo: Double,
    nodedot: Double,
    xpidot: Double,
    z1: Double,
    z3: Double,
    z11: Double,
    z13: Double,
    z21: Double,
    z23: Double,
    z31: Double,
    z33: Double,
    ecco: Double,
    eccsq: Double,
    em: Double,
) {
    val q22 = 1.7891679e-6
    val q31 = 2.1460748e-6
    val q33 = 2.2123015e-7
    val root22 = 1.7891679e-6
    val root44 = 7.3636953e-9
    val root54 = 2.1765803e-9
    val rptim = 4.37526908801129966e-3
    val root32 = 3.7393792e-7
    val root52 = 1.1428639e-7
    val x2o3 = 2.0 / 3.0
    val znl = 1.5835218e-4
    val zns = 1.19459e-5

    var irez = 0
    val nm = no
    if (nm > 0.0034906585 && nm < 0.0052359877) irez = 1
    if (nm in 8.26e-3..9.24e-3 && em >= 0.5) irez = 2

    val ses = ss1 * zns * ss5
    val sis = ss2 * zns * (sz11 + sz13)
    val sls = -zns * ss3 * (sz1 + sz3 - 14.0 - 6.0 * emsq)
    val sghs = ss4 * zns * (sz31 + sz33 - 6.0)
    var shs = -zns * ss2 * (sz21 + sz23)
    if (sat.inclo < 5.2359877e-2 || sat.inclo > PI - 5.2359877e-2) shs = 0.0
    if (sinim != 0.0) shs /= sinim
    val sgs = sghs - cosim * shs

    sat.dedt = ses + s1 * znl * s5
    sat.didt = sis + s2 * znl * (z11 + z13)
    sat.dmdt = sls - znl * s3 * (z1 + z3 - 14.0 - 6.0 * emsq)
    val sghl = s4 * znl * (z31 + z33 - 6.0)
    var shll = -znl * s2 * (z21 + z23)
    if (sat.inclo < 5.2359877e-2 || sat.inclo > PI - 5.2359877e-2) shll = 0.0
    sat.domdt = sgs + sghl
    sat.dnodt = shs
    if (sinim != 0.0) {
        sat.domdt -= cosim / sinim * shll
        sat.dnodt += shll / sinim
    }

    val theta = pmod(gsto + tc * rptim, TWOPI)
    var emLocal = em + sat.dedt * t
    if (irez != 0) {
        val aonv = (nm / XKE).pow(x2o3)
        if (irez == 2) {
            val cosisq = cosim * cosim
            emLocal = ecco
            val emsqLocal = eccsq
            val eoc = emLocal * emsqLocal
            val g201 = -0.306 - (emLocal - 0.64) * 0.440
            val g211: Double
            val g310: Double
            val g322: Double
            val g410: Double
            val g422: Double
            val g520: Double
            if (emLocal <= 0.65) {
                g211 = 3.616 - 13.2470 * emLocal + 16.2900 * emsqLocal
                g310 = -19.302 + 117.3900 * emLocal - 228.4190 * emsqLocal + 156.5910 * eoc
                g322 = -18.9068 + 109.7927 * emLocal - 214.6334 * emsqLocal + 146.5816 * eoc
                g410 = -41.122 + 242.6940 * emLocal - 471.0940 * emsqLocal + 313.9530 * eoc
                g422 = -146.407 + 841.8800 * emLocal - 1629.014 * emsqLocal + 1083.4350 * eoc
                g520 = -532.114 + 3017.977 * emLocal - 5740.032 * emsqLocal + 3708.2760 * eoc
            } else {
                g211 = -72.099 + 331.819 * emLocal - 508.738 * emsqLocal + 266.724 * eoc
                g310 = -346.844 + 1582.851 * emLocal - 2415.925 * emsqLocal + 1246.113 * eoc
                g322 = -342.585 + 1554.908 * emLocal - 2366.899 * emsqLocal + 1215.972 * eoc
                g410 = -1052.797 + 4758.686 * emLocal - 7193.992 * emsqLocal + 3651.957 * eoc
                g422 = -3581.690 + 16178.110 * emLocal - 24462.770 * emsqLocal + 12422.520 * eoc
                g520 = if (emLocal > 0.715) {
                    -5149.66 + 29936.92 * emLocal - 54087.36 * emsqLocal + 31324.56 * eoc
                } else {
                    1464.74 - 4664.75 * emLocal + 3763.64 * emsqLocal
                }
            }
            val g533: Double
            val g521: Double
            val g532: Double
            if (emLocal < 0.7) {
                g533 = -919.22770 + 4988.6100 * emLocal - 9064.7700 * emsqLocal + 5542.21 * eoc
                g521 = -822.71072 + 4568.6173 * emLocal - 8491.4146 * emsqLocal + 5337.524 * eoc
                g532 = -853.66600 + 4690.2500 * emLocal - 8624.7700 * emsqLocal + 5341.4 * eoc
            } else {
                g533 = -37995.780 + 161616.52 * emLocal - 229838.20 * emsqLocal + 109377.94 * eoc
                g521 = -51752.104 + 218913.95 * emLocal - 309468.16 * emsqLocal + 146349.42 * eoc
                g532 = -40023.880 + 170470.89 * emLocal - 242699.48 * emsqLocal + 115605.82 * eoc
            }
            val sini2 = sinim * sinim
            val f220 = 0.75 * (1.0 + 2.0 * cosim + cosisq)
            val f221 = 1.5 * sini2
            val f321 = 1.875 * sinim * (1.0 - 2.0 * cosim - 3.0 * cosisq)
            val f322 = -1.875 * sinim * (1.0 + 2.0 * cosim - 3.0 * cosisq)
            val f441 = 35.0 * sini2 * f220
            val f442 = 39.3750 * sini2 * sini2
            val f522 = 9.84375 * sinim * (
                sini2 * (1.0 - 2.0 * cosim - 5.0 * cosisq) +
                    0.33333333 * (-2.0 + 4.0 * cosim + 6.0 * cosisq)
                )
            val f523 = sinim * (
                4.92187512 * sini2 * (-2.0 - 4.0 * cosim + 10.0 * cosisq) +
                    6.56250012 * (1.0 + 2.0 * cosim - 3.0 * cosisq)
                )
            val f542 = 29.53125 * sinim * (2.0 - 8.0 * cosim + cosisq * (-12.0 + 8.0 * cosim + 10.0 * cosisq))
            val f543 = 29.53125 * sinim * (-2.0 - 8.0 * cosim + cosisq * (12.0 + 8.0 * cosim - 10.0 * cosisq))
            val xno2 = nm * nm
            val ainv2 = aonv * aonv
            var temp1 = 3.0 * xno2 * ainv2
            var temp = temp1 * root22
            sat.d2201 = temp * f220 * g201
            sat.d2211 = temp * f221 * g211
            temp1 *= aonv
            temp = temp1 * root32
            sat.d3210 = temp * f321 * g310
            sat.d3222 = temp * f322 * g322
            temp1 *= aonv
            temp = 2.0 * temp1 * root44
            sat.d4410 = temp * f441 * g410
            sat.d4422 = temp * f442 * g422
            temp1 *= aonv
            temp = temp1 * root52
            sat.d5220 = temp * f522 * g520
            sat.d5232 = temp * f523 * g532
            temp = 2.0 * temp1 * root54
            sat.d5421 = temp * f542 * g521
            sat.d5433 = temp * f543 * g533
            sat.xlamo = pmod(mo + nodeo + nodeo - theta - theta, TWOPI)
            sat.xfact = mdot + sat.dmdt + 2.0 * (nodedot + sat.dnodt - rptim) - no
        }
        if (irez == 1) {
            val g200 = 1.0 + emsq * (-2.5 + 0.8125 * emsq)
            val g310 = 1.0 + 2.0 * emsq
            val g300 = 1.0 + emsq * (-6.0 + 6.60937 * emsq)
            val f220 = 0.75 * (1.0 + cosim) * (1.0 + cosim)
            val f311 = 0.9375 * sinim * sinim * (1.0 + 3.0 * cosim) - 0.75 * (1.0 + cosim)
            var f330 = 1.0 + cosim
            f330 = 1.875 * f330 * f330 * f330
            var del1 = 3.0 * nm * nm * aonv * aonv
            sat.del2 = 2.0 * del1 * f220 * g200 * q22
            sat.del3 = 3.0 * del1 * f330 * g300 * q33 * aonv
            del1 *= f311 * g310 * q31 * aonv
            sat.del1 = del1
            sat.xlamo = pmod(mo + nodeo + argpo - theta, TWOPI)
            sat.xfact = mdot + xpidot - rptim + sat.dmdt + sat.domdt + sat.dnodt - no
        }
        sat.xli = sat.xlamo
        sat.xni = no
        sat.atime = 0.0
    }
    sat.irez = irez
}

private data class DeepUpdate(
    val em: Double,
    val argpm: Double,
    val inclm: Double,
    val mm: Double,
    val nodem: Double,
    val nm: Double,
)

private fun dspace(
    sat: Sat,
    emIn: Double,
    argpmIn: Double,
    inclmIn: Double,
    mmIn: Double,
    nodemIn: Double,
    nmIn: Double,
    tc: Double,
): DeepUpdate {
    val fasx2 = 0.13130908
    val fasx4 = 2.8843198
    val fasx6 = 0.37448087
    val g22 = 5.7686396
    val g32 = 0.95240898
    val g44 = 1.8014998
    val g52 = 1.0508330
    val g54 = 4.4108898
    val rptim = 4.37526908801129966e-3
    val stepp = 720.0
    val stepn = -720.0
    val step2 = 259200.0

    var dndt = 0.0
    val theta = pmod(sat.gsto + tc * rptim, TWOPI)
    var em = emIn + sat.dedt * sat.t
    var inclm = inclmIn + sat.didt * sat.t
    var argpm = argpmIn + sat.domdt * sat.t
    var nodem = nodemIn + sat.dnodt * sat.t
    var mm = mmIn + sat.dmdt * sat.t
    var nm = nmIn
    if (sat.irez != 0) {
        var atime = sat.atime
        var xni = sat.xni
        var xli = sat.xli
        if (atime == 0.0 || sat.t * atime <= 0.0 || abs(sat.t) < abs(atime)) {
            atime = 0.0
            xni = sat.noUnkozai
            xli = sat.xlamo
        }
        val delt = if (sat.t > 0.0) stepp else stepn
        var iretn = 381
        var ft = 0.0
        var xldot = 0.0
        var xndt = 0.0
        var xnddt = 0.0
        var guard = 0
        while (iretn == 381) {
            if (++guard > 100_000) throw Sgp4Exception("SGP4 error 2: deep-space integrator did not converge")
            if (sat.irez != 2) {
                xndt = sat.del1 * sin(xli - fasx2) + sat.del2 * sin(2.0 * (xli - fasx4)) + sat.del3 * sin(3.0 * (xli - fasx6))
                xldot = xni + sat.xfact
                xnddt = sat.del1 * cos(xli - fasx2) +
                    2.0 * sat.del2 * cos(2.0 * (xli - fasx4)) +
                    3.0 * sat.del3 * cos(3.0 * (xli - fasx6))
                xnddt *= xldot
            } else {
                val xomi = sat.argpo + sat.argpdot * atime
                val x2omi = xomi + xomi
                val x2li = xli + xli
                xndt = sat.d2201 * sin(x2omi + xli - g22) + sat.d2211 * sin(xli - g22) +
                    sat.d3210 * sin(xomi + xli - g32) + sat.d3222 * sin(-xomi + xli - g32) +
                    sat.d4410 * sin(x2omi + x2li - g44) + sat.d4422 * sin(x2li - g44) +
                    sat.d5220 * sin(xomi + xli - g52) + sat.d5232 * sin(-xomi + xli - g52) +
                    sat.d5421 * sin(xomi + x2li - g54) + sat.d5433 * sin(-xomi + x2li - g54)
                xldot = xni + sat.xfact
                xnddt = sat.d2201 * cos(x2omi + xli - g22) + sat.d2211 * cos(xli - g22) +
                    sat.d3210 * cos(xomi + xli - g32) + sat.d3222 * cos(-xomi + xli - g32) +
                    sat.d5220 * cos(xomi + xli - g52) + sat.d5232 * cos(-xomi + xli - g52) +
                    2.0 * (
                        sat.d4410 * cos(x2omi + x2li - g44) + sat.d4422 * cos(x2li - g44) +
                            sat.d5421 * cos(xomi + x2li - g54) + sat.d5433 * cos(-xomi + x2li - g54)
                        )
                xnddt *= xldot
            }
            if (abs(sat.t - atime) >= stepp) {
                iretn = 381
            } else {
                ft = sat.t - atime
                iretn = 0
            }
            if (iretn == 381) {
                xli += xldot * delt + xndt * step2
                xni += xndt * delt + xnddt * step2
                atime += delt
            }
        }
        nm = xni + xndt * ft + xnddt * ft * ft * 0.5
        val xl = xli + xldot * ft + xndt * ft * ft * 0.5
        if (sat.irez != 1) {
            mm = xl - 2.0 * nodem + 2.0 * theta
            dndt = nm - sat.noUnkozai
        } else {
            mm = xl - nodem - argpm + theta
            dndt = nm - sat.noUnkozai
        }
        nm = sat.noUnkozai + dndt
    }
    return DeepUpdate(em, argpm, inclm, mm, nodem, nm)
}

private fun dpper(sat: Sat, init: Boolean, ep: Double, inclp: Double, nodep: Double, argpp: Double, mp: Double): Mean {
    val zns = 1.19459e-5
    val zes = 0.01675
    val znl = 1.5835218e-4
    val zel = 0.05490
    var zm = if (init) sat.zmos else sat.zmos + zns * sat.t
    var zf = zm + 2.0 * zes * sin(zm)
    var sinzf = sin(zf)
    var f2 = 0.5 * sinzf * sinzf - 0.25
    var f3 = -0.5 * sinzf * cos(zf)
    val ses = sat.se2 * f2 + sat.se3 * f3
    val sis = sat.si2 * f2 + sat.si3 * f3
    val sls = sat.sl2 * f2 + sat.sl3 * f3 + sat.sl4 * sinzf
    val sghs = sat.sgh2 * f2 + sat.sgh3 * f3 + sat.sgh4 * sinzf
    val shs = sat.sh2 * f2 + sat.sh3 * f3
    zm = if (init) sat.zmol else sat.zmol + znl * sat.t
    zf = zm + 2.0 * zel * sin(zm)
    sinzf = sin(zf)
    f2 = 0.5 * sinzf * sinzf - 0.25
    f3 = -0.5 * sinzf * cos(zf)
    val sel = sat.ee2 * f2 + sat.e3 * f3
    val sil = sat.xi2 * f2 + sat.xi3 * f3
    val sll = sat.xl2 * f2 + sat.xl3 * f3 + sat.xl4 * sinzf
    val sghl = sat.xgh2 * f2 + sat.xgh3 * f3 + sat.xgh4 * sinzf
    val shll = sat.xh2 * f2 + sat.xh3 * f3
    var pe = ses + sel
    var pinc = sis + sil
    var pl = sls + sll
    var pgh = sghs + sghl
    var ph = shs + shll
    var epOut = ep
    var inclpOut = inclp
    var nodepOut = nodep
    var argppOut = argpp
    var mpOut = mp
    if (!init) {
        pe -= sat.peo
        pinc -= sat.pinco
        pl -= sat.plo
        pgh -= sat.pgho
        ph -= sat.pho
        inclpOut += pinc
        epOut += pe
        val sinip = sin(inclpOut)
        val cosip = cos(inclpOut)
        if (inclpOut >= 0.2) {
            ph /= sinip
            pgh -= cosip * ph
            argppOut += pgh
            nodepOut += ph
            mpOut += pl
        } else {
            val sinop = sin(nodepOut)
            val cosop = cos(nodepOut)
            var alfdp = sinip * sinop
            var betdp = sinip * cosop
            val dalf = ph * cosop + pinc * cosip * sinop
            val dbet = -ph * sinop + pinc * cosip * cosop
            alfdp += dalf
            betdp += dbet
            nodepOut = if (nodepOut >= 0.0) pmod(nodepOut, TWOPI) else -pmod(-nodepOut, TWOPI)
            if (nodepOut < 0.0 && OPSMODE == 'a') nodepOut += TWOPI
            val xls = mpOut + argppOut + pl + pgh + (cosip - pinc * sinip) * nodepOut
            val xnoh = nodepOut
            nodepOut = atan2(alfdp, betdp)
            if (nodepOut < 0.0 && OPSMODE == 'a') nodepOut += TWOPI
            if (abs(xnoh - nodepOut) > PI) {
                nodepOut += if (nodepOut < xnoh) TWOPI else -TWOPI
            }
            mpOut += pl
            argppOut = xls - mpOut - cosip * nodepOut
        }
    }
    return Mean(epOut, inclpOut, nodepOut, argppOut, mpOut)
}

private fun sgp4(sat: Sat, tsince: Double): Tem {
    val temp4 = 1.5e-12
    val x2o3 = 2.0 / 3.0
    val vkmpersec = RADIUS_EARTH_KM * XKE / 60.0
    sat.t = tsince
    val xmdf = sat.mo + sat.mdot * sat.t
    val argpdf = sat.argpo + sat.argpdot * sat.t
    val nodedf = sat.nodeo + sat.nodedot * sat.t
    var argpm = argpdf
    var mm = xmdf
    val t2 = sat.t * sat.t
    var nodem = nodedf + sat.nodecf * t2
    var tempa = 1.0 - sat.cc1 * sat.t
    var tempe = sat.bstar * sat.cc4 * sat.t
    var templ = sat.t2cof * t2
    if (sat.isimp != 1) {
        val delomg = sat.omgcof * sat.t
        val delmtemp = 1.0 + sat.eta * cos(xmdf)
        val delm = sat.xmcof * (delmtemp * delmtemp * delmtemp - sat.delmo)
        val temp = delomg + delm
        mm = xmdf + temp
        argpm = argpdf - temp
        val t3 = t2 * sat.t
        val t4 = t3 * sat.t
        tempa = tempa - sat.d2 * t2 - sat.d3 * t3 - sat.d4 * t4
        tempe += sat.bstar * sat.cc5 * (sin(mm) - sat.sinmao)
        templ = templ + sat.t3cof * t3 + t4 * (sat.t4cof + sat.t * sat.t5cof)
    }
    var nm = sat.noUnkozai
    var em = sat.ecco
    var inclm = sat.inclo
    if (sat.method == 'd') {
        val updated = dspace(sat, em, argpm, inclm, mm, nodem, nm, sat.t)
        em = updated.em
        argpm = updated.argpm
        inclm = updated.inclm
        mm = updated.mm
        nodem = updated.nodem
        nm = updated.nm
    }
    if (nm <= 0.0) throw Sgp4Exception("SGP4 error 2: mean motion $nm is less than zero")
    val am = (XKE / nm).pow(x2o3) * tempa * tempa
    nm = XKE / am.pow(1.5)
    em -= tempe
    if (em >= 1.0 || em < -0.001) {
        throw Sgp4Exception("SGP4 error 1: mean eccentricity $em is outside 0.0 <= e < 1.0")
    }
    if (em < 1.0e-6) em = 1.0e-6
    mm += sat.noUnkozai * templ
    val xlm = mm + argpm + nodem
    nodem = if (nodem >= 0.0) pmod(nodem, TWOPI) else -pmod(-nodem, TWOPI)
    argpm = pmod(argpm, TWOPI)
    val xlmWrapped = pmod(xlm, TWOPI)
    mm = pmod(xlmWrapped - argpm - nodem, TWOPI)

    var ep = em
    var xincp = inclm
    var argpp = argpm
    var nodep = nodem
    var mp = mm
    if (sat.method == 'd') {
        val periodic = dpper(sat, init = false, ep = ep, inclp = xincp, nodep = nodep, argpp = argpp, mp = mp)
        ep = periodic.ep
        xincp = periodic.inclp
        nodep = periodic.nodep
        argpp = periodic.argpp
        mp = periodic.mp
        if (xincp < 0.0) {
            xincp = -xincp
            nodep += PI
            argpp -= PI
        }
        if (ep < 0.0 || ep > 1.0) {
            throw Sgp4Exception("SGP4 error 3: perturbed eccentricity $ep is outside 0.0 <= e <= 1.0")
        }
        val sinip = sin(xincp)
        val cosip = cos(xincp)
        sat.aycof = -0.5 * J3OJ2 * sinip
        sat.xlcof = if (abs(cosip + 1.0) > 1.5e-12) {
            -0.25 * J3OJ2 * sinip * (3.0 + 5.0 * cosip) / (1.0 + cosip)
        } else {
            -0.25 * J3OJ2 * sinip * (3.0 + 5.0 * cosip) / temp4
        }
    }
    val axnl = ep * cos(argpp)
    val temp = 1.0 / (am * (1.0 - ep * ep))
    val aynl = ep * sin(argpp) + temp * sat.aycof
    val xl = mp + argpp + nodep + temp * sat.xlcof * axnl
    val u = pmod(xl - nodep, TWOPI)
    var eo1 = u
    var tem5 = 9999.9
    var ktr = 1
    var sineo1 = 0.0
    var coseo1 = 0.0
    while (abs(tem5) >= 1.0e-12 && ktr <= 10) {
        sineo1 = sin(eo1)
        coseo1 = cos(eo1)
        tem5 = 1.0 - coseo1 * axnl - sineo1 * aynl
        tem5 = (u - aynl * coseo1 + axnl * sineo1 - eo1) / tem5
        if (abs(tem5) >= 0.95) tem5 = if (tem5 > 0.0) 0.95 else -0.95
        eo1 += tem5
        ktr += 1
    }
    val ecose = axnl * coseo1 + aynl * sineo1
    val esine = axnl * sineo1 - aynl * coseo1
    val el2 = axnl * axnl + aynl * aynl
    val pl = am * (1.0 - el2)
    if (pl < 0.0) throw Sgp4Exception("SGP4 error 4: semilatus rectum $pl is less than zero")
    val rl = am * (1.0 - ecose)
    val rdotl = sqrt(am) * esine / rl
    val rvdotl = sqrt(pl) / rl
    val betal = sqrt(1.0 - el2)
    val tempShort = esine / (1.0 + betal)
    val sinu = am / rl * (sineo1 - aynl - axnl * tempShort)
    val cosu = am / rl * (coseo1 - axnl + aynl * tempShort)
    var su = atan2(sinu, cosu)
    val sin2u = (cosu + cosu) * sinu
    val cos2u = 1.0 - 2.0 * sinu * sinu
    val tempPl = 1.0 / pl
    val temp1 = 0.5 * J2 * tempPl
    val temp2 = temp1 * tempPl
    var cosip = 0.0
    var sinip = 0.0
    if (sat.method == 'd') {
        sinip = sin(xincp)
        cosip = cos(xincp)
        val cosisq = cosip * cosip
        sat.con41 = 3.0 * cosisq - 1.0
        sat.x1mth2 = 1.0 - cosisq
        sat.x7thm1 = 7.0 * cosisq - 1.0
    } else {
        sinip = sin(xincp)
        cosip = cos(xincp)
    }
    val mrt = rl * (1.0 - 1.5 * temp2 * betal * sat.con41) + 0.5 * temp1 * sat.x1mth2 * cos2u
    su -= 0.25 * temp2 * sat.x7thm1 * sin2u
    val xnode = nodep + 1.5 * temp2 * cosip * sin2u
    val xinc = xincp + 1.5 * temp2 * cosip * sinip * cos2u
    val mvt = rdotl - nm * temp1 * sat.x1mth2 * sin2u / XKE
    val rvdot = rvdotl + nm * temp1 * (sat.x1mth2 * cos2u + 1.5 * sat.con41) / XKE
    val sinsu = sin(su)
    val cossu = cos(su)
    val snod = sin(xnode)
    val cnod = cos(xnode)
    val sini = sin(xinc)
    val cosi = cos(xinc)
    val xmx = -snod * cosi
    val xmy = cnod * cosi
    val ux = xmx * sinsu + cnod * cossu
    val uy = xmy * sinsu + snod * cossu
    val uz = sini * sinsu
    val vx = xmx * cossu - cnod * sinsu
    val vy = xmy * cossu - snod * sinsu
    val vz = sini * cossu
    if (mrt < 1.0) throw Sgp4Exception("SGP4 error 6: satellite has decayed (mrt=$mrt)")
    val mr = mrt * RADIUS_EARTH_KM
    return Tem(
        xKm = mr * ux,
        yKm = mr * uy,
        zKm = mr * uz,
        vxKmPerSec = (mvt * ux + rvdot * vx) * vkmpersec,
        vyKmPerSec = (mvt * uy + rvdot * vy) * vkmpersec,
        vzKmPerSec = (mvt * uz + rvdot * vz) * vkmpersec,
    )
}

/** Python `%` for a positive modulus: the remainder is never negative. */
private fun pmod(value: Double, modulus: Double): Double {
    val remainder = value % modulus
    return if (remainder < 0.0) remainder + modulus else remainder
}
