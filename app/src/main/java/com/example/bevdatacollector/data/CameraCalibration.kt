package com.example.bevdatacollector.data

import com.google.gson.annotations.SerializedName

data class CameraCalibration(
    @SerializedName("fx") val fx: Float,
    @SerializedName("fy") val fy: Float,
    @SerializedName("cx") val cx: Float,
    @SerializedName("cy") val cy: Float,
    @SerializedName("k1") val k1: Float,
    @SerializedName("k2") val k2: Float,
    @SerializedName("p1") val p1: Float,
    @SerializedName("p2") val p2: Float,
    @SerializedName("k3") val k3: Float,
    @SerializedName("cameraHeightMeters") val cameraHeightMeters: Float,
    @SerializedName("gpsOffsetX") val gpsOffsetX: Float,
    @SerializedName("gpsOffsetY") val gpsOffsetY: Float,
    @SerializedName("gpsOffsetZ") val gpsOffsetZ: Float,
    @SerializedName("isCalibrated") val isCalibrated: Boolean = false
) {
    fun toCsvHeader(): String = "fx,fy,cx,cy,k1,k2,p1,p2,k3,cameraHeight,gpsOffsetX,gpsOffsetY,gpsOffsetZ"

    fun toCsvRow(): String = "$fx,$fy,$cx,$cy,$k1,$k2,$p1,$p2,$k3,$cameraHeightMeters,$gpsOffsetX,$gpsOffsetY,$gpsOffsetZ"

    fun isValid(): Boolean = isCalibrated

    fun hasValidValues(): Boolean = fx > 0 && fy > 0 && cx > 0 && cy > 0

    fun getDistortionCoefficients(): FloatArray {
        return floatArrayOf(k1, k2, p1, p2, k3)
    }

    fun getIntrinsicMatrix(): Array<FloatArray> {
        return arrayOf(
            floatArrayOf(fx, 0f, cx),
            floatArrayOf(0f, fy, cy),
            floatArrayOf(0f, 0f, 1f)
        )
    }

    companion object {
        fun default(): CameraCalibration = CameraCalibration(
            fx = 0f,
            fy = 0f,
            cx = 0f,
            cy = 0f,
            k1 = 0f,
            k2 = 0f,
            p1 = 0f,
            p2 = 0f,
            k3 = 0f,
            cameraHeightMeters = 1.7f,
            gpsOffsetX = 0f,
            gpsOffsetY = 0f,
            gpsOffsetZ = 0f,
            isCalibrated = false
        )

        fun calibrated(
            fx: Float, fy: Float, cx: Float, cy: Float,
            k1: Float, k2: Float, p1: Float, p2: Float, k3: Float,
            cameraHeightMeters: Float, gpsOffsetX: Float, gpsOffsetY: Float, gpsOffsetZ: Float
        ): CameraCalibration = CameraCalibration(
            fx = fx,
            fy = fy,
            cx = cx,
            cy = cy,
            k1 = k1,
            k2 = k2,
            p1 = p1,
            p2 = p2,
            k3 = k3,
            cameraHeightMeters = cameraHeightMeters,
            gpsOffsetX = gpsOffsetX,
            gpsOffsetY = gpsOffsetY,
            gpsOffsetZ = gpsOffsetZ,
            isCalibrated = true
        )
    }

    override fun toString(): String {
        return """
            CameraCalibration:
              Calibrated: $isCalibrated
              Intrinsics: fx=$fx, fy=$fy, cx=$cx, cy=$cy
              Distortion: k1=$k1, k2=$k2, p1=$p1, p2=$p2, k3=$k3
              Setup: height=${cameraHeightMeters}m, GPS offset=($gpsOffsetX, $gpsOffsetY, $gpsOffsetZ)
        """.trimIndent()
    }
}