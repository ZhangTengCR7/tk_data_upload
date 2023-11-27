package com.xayc.dataacquisition;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.text.TextUtils;

import com.mor.dataacquisition.db.DBManager;
import com.mor.dataacquisition.location.Location;
import com.mor.dataacquisition.net.HttpService;
import com.mor.dataacquisition.net.ThreadUpLocAndStep;
import com.mor.dataacquisition.net.dataCallBacks.CJBCInitDataCallBack;
import com.mor.dataacquisition.net.dataCallBacks.CJUpCmDataCallBack;
import com.mor.dataacquisition.net.dataCallBacks.CJUpOriginalDataCallBack;
import com.mor.dataacquisition.net.dataCallBacks.CJUpRecordInitDataCallBack;
import com.mor.dataacquisition.net.dataCallBacks.CJUpResultDataCallBack;
import com.mor.dataacquisition.net.parsedData.CJBCResult;
import com.mor.dataacquisition.net.parsedData.CJCmResult;
import com.mor.dataacquisition.net.parsedData.CJResutResult;
import com.mor.dataacquisition.sha.FileUtil;
import com.mor.dataacquisition.sha.ShaUtil;
import com.mor.dataacquisition.step.CountStep;
import com.mor.dataacquisition.struct.AClass;
import com.mor.dataacquisition.struct.BClass;
import com.mor.dataacquisition.struct.ENCBClass;
import com.mor.dataacquisition.util.ApkUtil;
import com.mor.dataacquisition.util.CountDown;
import com.mor.dataacquisition.util.DataCheck;
import com.mor.dataacquisition.util.DateUtil;
import com.mor.dataacquisition.util.DeviceUtil;
import com.mor.dataacquisition.util.GpsUtil;
import com.mor.dataacquisition.util.MyLog;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class DataAcquisition {
    private static DataAcquisition mDataAcquisition;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    private Location baidu_loc;
    private SharedPreferences SP;
    private boolean hasUUID_SP = false;
    private boolean hasoffset_init = false;
    private double offset_latitude;
    private double offset_longitude;
    private boolean isFirstStep = false;
    private Context context;
    private CountDown countdown;

    /**
     * 是否是正式版上线产品
     * 正式版：上线版，MD5码自动获取，不固定
     * 测试版：用于测试，MD5码固定为 testCode
     */
    private boolean isNormalVersion = false;
    /**
     * 测试时用的MD5码
     */
//    private String testCode = "a1e14505d8a93de281b09c2e6c2d3a38";
//    private String testCode = "02a097e3cf1b9d301103257e93b64ab6";
    //private String testCode = "fa638c2bd3567d4181a29232ece48f0b";
    //private String testCode = "c6722e6170cd8d1401b479aa2c753ca0";
    //private String testCode = "149a8292b9ebd0ec7f6ab52bbe38cd14";
    private String testCode = "76264922341446d3a14621b8f6f9f877";

    private DataAcquisition() {
    }

    public static DataAcquisition getInstance() {
        if (mDataAcquisition == null) {
            mDataAcquisition = new DataAcquisition();
        }

        return mDataAcquisition;
    }

    public void CJUpResult(final AClass[] alist, final String sdata, final String sjid, final String nyid, final String remark, final String account, final String pwd, final String sname, final String scard, final String uuid, final Context context, final CJUpResultDataCallBack dataCallBack) {
        String unique_id = DeviceUtil.uniqueID(context);
        String mac = DeviceUtil.macAddress(context);
        MyLog.i("zsy", "mac is:" + mac);
        if (MetaData.getInstance().isInited() && MetaData.getInstance().isTodaysData()) {
            MyLog.i("zsy", "had been inited");
            int checkCode = DataCheck.checkCJUpResult(alist, sdata, sjid, nyid, remark, account, pwd, sname, scard, uuid);
            if (checkCode != 0) {
                MyLog.i("zsy", "checkCodeError" + checkCode);
                CJResutResult result = new CJResutResult();
                result.returnCode = checkCode;
                dataCallBack.processData(result);
            } else {
                String md5 = "";
                if (isNormalVersion) {
                    String path = ApkUtil.getApkPath(context);
                    MyLog.i("zsy", "apkpath = " + path);
                    md5 = FileUtil.file_token(path);
                } else {
                    md5 = testCode;
                }
                MyLog.i("zsy", "sha:" + md5);
                md5 = ShaUtil.sha(md5, MetaData.getInstance().getModulus());
                md5 = FileUtil.file_mark(MetaData.getInstance().getPublicE(), MetaData.getInstance().getModulus(), md5);
                DBManager db = DBManager.getInstance(context);
                if (!DateUtil.CheckNowDateLess20160905(db, uuid)) {
                    (new ThreadUpLocAndStep(context, uuid, nyid)).run();
                }

                HttpService.CJUpResult(alist, sdata, sjid, nyid, remark, account, pwd, sname, scard, unique_id, md5, mac, context, dataCallBack);
            }
        } else {
            HttpService.initKeys(unique_id, mac, account, new CJUpCmDataCallBack() {
                public void processData(CJCmResult data) {
                    if (data.returnCode != 0 && !TextUtils.isEmpty(data.modulus) && data.public_e != 0L) {
                        MyLog.i("zsy", "init success");
                        MetaData.getInstance().setPublicEAndModulus(data.public_e, data.modulus);
                        DataAcquisition.this.CJUpResult(alist, sdata, sjid, nyid, remark, account, pwd, sname, scard, uuid, context, dataCallBack);
                    } else {
                        MyLog.i("zsy", "init failed");
                        CJResutResult cjResult = new CJResutResult();
                        cjResult.returnCode = -15;
                        dataCallBack.processData(cjResult);
                    }

                    if (data.returnCode == 0) {
                        DataAcquisition.this.delRecords(context, uuid);
                    }

                }
            }, context);
        }
    }

    public void CJUpOriginal(final BClass[] blist, final String equipbrand, final String instrumodel, final String serialnum, final String sjid, final String temperature, final String barometric, final String weather, final String benchmarkids, final String mtype, final String mdate, final String linecode, final String account, final String pwd, final String uuid, final String benchmarkv, final Context context, final CJUpOriginalDataCallBack dataCallBack) {
        String unique_id = DeviceUtil.uniqueID(context);
        String mac = DeviceUtil.macAddress(context);
        if (MetaData.getInstance().isInited() && MetaData.getInstance().isTodaysData()) {
            MyLog.i("zsy", "had been inited");
            int checkCode = DataCheck.checkCJUpOriginal(blist, equipbrand, instrumodel, serialnum, sjid, temperature, barometric, weather, benchmarkids, mtype, mdate, linecode, account, pwd, uuid, benchmarkv);
            if (checkCode != 0) {
                MyLog.i("zsy", "checkCodeError" + checkCode);
                CJResutResult result = new CJResutResult();
                result.returnCode = checkCode;
                dataCallBack.processData(result);
            } else {
                DBManager db = DBManager.getInstance(context);
                List<ENCBClass> encbs = null;
                if (DateUtil.CheckNowDateLess20160905(db, uuid)) {
                    encbs = db.Temp_getAllRecords();
                } else {
                    encbs = db.getAllRecords(uuid);
                }

                if (encbs.size() == 0) {
                    CJResutResult cjResult = new CJResutResult();
                    cjResult.returnCode = -18;
                    dataCallBack.processData(cjResult);
                } else {
                    if (encbs.size() != 0) {
                        MyLog.i("zsy", "encbs class from db is:" + encbs);
                    }
                    String md5 = "";
                    if (isNormalVersion) {
                        String path = ApkUtil.getApkPath(context);
                        MyLog.i("zsy", "apkpath = " + path);
                        md5 = FileUtil.file_token(path);
                    } else {
                        //版本3.5.1的MD5值
                        md5 = testCode;
                    }
                    MyLog.i("zsy", "md5:" + md5);
                    md5 = ShaUtil.sha(md5, MetaData.getInstance().getModulus());
                    MyLog.i("zsy", "sha:" + md5);
                    md5 = FileUtil.file_mark(MetaData.getInstance().getPublicE(), MetaData.getInstance().getModulus(), md5);
                    HttpService.CJUpOriginal(blist, equipbrand, instrumodel, serialnum, sjid, temperature, barometric, weather, benchmarkids, mtype, mdate, linecode, account, pwd, unique_id, md5, mac, encbs, benchmarkv, context, new CJUpOriginalDataCallBack() {
                        public void processData(CJResutResult data) {
                            dataCallBack.processData(data);
                        }
                    });
                }
            }
        } else {
            HttpService.initKeys(unique_id, mac, account, new CJUpCmDataCallBack() {
                public void processData(CJCmResult data) {
                    if (data.returnCode != 0 && !TextUtils.isEmpty(data.modulus) && data.public_e != 0L) {
                        MyLog.i("zsy", "init success");
                        MetaData.getInstance().setPublicEAndModulus(data.public_e, data.modulus);
                        DataAcquisition.this.CJUpOriginal(blist, equipbrand, instrumodel, serialnum, sjid, temperature, barometric, weather, benchmarkids, mtype, mdate, linecode, account, pwd, uuid, benchmarkv, context, dataCallBack);
                    } else {
                        MyLog.i("zsy", "init failed");
                        CJResutResult cjResult = new CJResutResult();
                        cjResult.returnCode = -15;
                        dataCallBack.processData(cjResult);
                    }

                }
            }, context);
        }
    }

    public void CjUpRecordInit(String account, final Context context, final CJUpRecordInitDataCallBack callBack) {
        if (account != null && !"".equals(account)) {
            String unique_id = DeviceUtil.uniqueID(context);
            String mac = DeviceUtil.macAddress(context);
            HttpService.initBCKeys(unique_id, mac, account, new CJBCInitDataCallBack() {
                public void processData(CJBCResult data) {
                    if (data.returnCode == 1) {
                        MyLog.i("zsy", "BC init OK with a is:" + data.modulus + ", e is:" + data.public_e);
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        String date = sdf.format(new Date());
                        DBManager db = DBManager.getInstance(context);
                        db.saveTodayE(data.modulus, String.valueOf(data.public_e), date, data.enc_id);
                        CJResutResult cjResutResultx = new CJResutResult();
                        cjResutResultx.returnCode = 0;
                        callBack.processData(cjResutResultx);
                    } else {
                        CJResutResult cjResutResult = new CJResutResult();
                        cjResutResult.returnCode = -1;
                        callBack.processData(cjResutResult);
                    }

                }
            }, context);
        } else {
            CJResutResult cjResutResult = new CJResutResult();
            cjResutResult.returnCode = -2;
            callBack.processData(cjResutResult);
        }
    }

    public int CjUpRecord(String bffb, String bfpcode, String bfpl, String bfpvalue, Context context) {
        boolean result = true;
        byte result2;
        if (!GpsUtil.gPSIsOPen(context)) {
            result2 = -78;
            return result2;
        } else {
            int result1 = DataCheck.checkCjUpRecord(bffb, bfpcode, bfpl, bfpvalue);
            if (result1 != 0) {
                return result1;
            } else {
                Calendar cal = Calendar.getInstance();
                String date = this.sdf.format(new Date());
                cal.setTime(new Date());
                cal.set(5, cal.get(5) - 1);
                String lastdate = this.sdf.format(cal.getTime());
                DBManager db = DBManager.getInstance(context);
                String[] encinfo = db.getENCINFO(date, lastdate);
                if (encinfo == null) {
                    result2 = -79;
                    return result2;
                } else {
                    String modulus = encinfo[0];
                    String pubE = encinfo[1];
                    String enc_id = encinfo[2];
                    long e = Long.parseLong(pubE);
                    String currentTime = DateUtil.now();
                    String sha_bffb = ShaUtil.shaRecordParameters(bffb, modulus);
                    String sha_bfpcode = ShaUtil.shaRecordParameters(bfpcode, modulus);
                    String sha_bfpl = ShaUtil.shaRecordParameters(bfpl, modulus);
                    String sha_bfpvalue = ShaUtil.shaRecordParameters(bfpvalue, modulus);
                    String sha_currentTime = ShaUtil.shaRecordParameters(currentTime, modulus);
                    MyLog.i("zsy", sha_bffb.length() + "-" + sha_bfpcode.length() + "-" + sha_bfpl.length() + "-" + sha_bfpvalue.length());
                    String enc_sha_bffb = FileUtil.file_mark(e, modulus, sha_bffb);
                    String enc_sha_bfpcode = FileUtil.file_mark(e, modulus, sha_bfpcode);
                    String enc_sha_bfpl = FileUtil.file_mark(e, modulus, sha_bfpl);
                    String enc_sha_bfpvalue = FileUtil.file_mark(e, modulus, sha_bfpvalue);
                    String enc_sha_currentTime = FileUtil.file_mark(e, modulus, sha_currentTime);
                    MyLog.i("zsy", enc_sha_bffb + "-" + enc_sha_bfpcode + "-" + enc_sha_bfpl + "-" + enc_sha_bfpvalue);
                    result1 = db.saveOneRecord(enc_sha_bffb, enc_sha_bfpcode, enc_sha_bfpl, enc_sha_bfpvalue, enc_sha_currentTime, enc_id, date);
                    if (result1 == -77) {
                        return result1;
                    } else {
                        return result1 == -1 ? result1 : result1;
                    }
                }
            }
        }
    }

    public int clearAll(Context context) {
        try {
            DBManager db = DBManager.getInstance(context);
            db.clearAllRecords();
            this.SP = context.getSharedPreferences("uuidloc", 0);
            Editor edit = this.SP.edit();
            edit.clear();
            edit.commit();
            return 0;
        } catch (Exception var4) {
            var4.printStackTrace();
            return -1;
        }
    }

    public int delRecords(Context context, String uuid) {
        try {
            DBManager db = DBManager.getInstance(context);
            if (DateUtil.CheckNowDateLess20160905(db, uuid)) {
                return 0;
            } else {
                db.delRecords(uuid);
                this.SP = context.getSharedPreferences("uuidloc", 0);
                Editor edit = this.SP.edit();
                edit.remove(uuid + "boolean");
                edit.remove(uuid + "latitude");
                edit.remove(uuid + "longitude");
                edit.commit();
                return 0;
            }
        } catch (Exception var5) {
            var5.printStackTrace();
            return -1;
        }
    }

    public String getUuid() {
        return UUID.randomUUID().toString();
    }

    public int setmeasureLineUuid(Context context, String uuid) {
        try {
            this.context = context;
            DBManager manager = DBManager.getInstance(context);
            manager.setmeasureLineUuid(uuid);
            return 0;
        } catch (Exception var4) {
            var4.printStackTrace();
            return -1;
        }
    }

    public int quitMeasureLine(Context context) {
        try {
            DBManager manager = DBManager.getInstance(context);
            manager.quitMeasureLine();
            return 0;
        } catch (Exception var3) {
            var3.printStackTrace();
            return -1;
        }
    }

    private int pauseMeasureLine() {
        try {
            DBManager manager = DBManager.getInstance(this.context);
            manager.pauseMeasureLine();
            return 0;
        } catch (Exception var2) {
            var2.printStackTrace();
            return -1;
        }
    }

    public int saveEarthPoint(int type, Context context) {
        this.monitorRefreshPause();
        Calendar cal = Calendar.getInstance();
        String date = this.sdf.format(new Date());
        cal.setTime(new Date());
        cal.set(5, cal.get(5) - 1);
        String lastdate = this.sdf.format(cal.getTime());
        DBManager db = DBManager.getInstance(context);
        String[] encinfo = db.getENCINFO(date, lastdate);
        if (encinfo == null) {
            int result = -1 / 0;
            return result;
        } else {
            String modulus = encinfo[0];
            String pubE = encinfo[1];
            String enc_id = encinfo[2];
            long e = Long.parseLong(pubE);
            String currentTime = DateUtil.now();
            this.baidu_loc = Location.getInstance();
            this.baidu_loc.flush();
            String sha_latitude;
            String sha_getlongitude;
            String enc_sha_latitude;
            String enc_sha_longitude;
            if (this.baidu_loc.isLocationOK()) {
                if (!this.check_Init_SharePref(context)) {
                    Editor edit = this.SP.edit();
                    this.offset_latitude = this.baidu_loc.getlatitude() - 1.0D;
                    this.offset_longitude = this.baidu_loc.getlongitude() - 1.0D;
                    edit.putString(db.getshare() + "latitude", String.valueOf(this.offset_latitude));
                    edit.putString(db.getshare() + "longitude", String.valueOf(this.offset_longitude));
                    edit.putBoolean(db.getshare() + "boolean", true);
                    edit.commit();
                    this.hasoffset_init = true;
                } else if (!this.hasoffset_init) {
                    this.offset_latitude = Double.parseDouble(this.SP.getString(db.getshare() + "latitude", "0.0"));
                    this.offset_longitude = Double.parseDouble(this.SP.getString(db.getshare() + "longitude", "0.0"));
                    this.hasoffset_init = true;
                }

                sha_latitude = ShaUtil.shaRecordParameters(String.format("%.5f", this.baidu_loc.getlatitude() - this.offset_latitude), modulus);
                sha_getlongitude = ShaUtil.shaRecordParameters(String.format("%.5f", this.baidu_loc.getlongitude() - this.offset_longitude), modulus);
                enc_sha_latitude = FileUtil.file_mark(e, modulus, sha_latitude);
                enc_sha_longitude = FileUtil.file_mark(e, modulus, sha_getlongitude);
            } else {
                sha_latitude = ShaUtil.shaRecordParameters("0.0", modulus);
                sha_getlongitude = ShaUtil.shaRecordParameters("0.0", modulus);
                enc_sha_latitude = FileUtil.file_mark(e, modulus, sha_latitude);
                enc_sha_longitude = FileUtil.file_mark(e, modulus, sha_getlongitude);
            }

            String sha_currentTime = ShaUtil.shaRecordParameters(currentTime, modulus);
            String sha_locType = ShaUtil.shaRecordParameters(String.valueOf(this.baidu_loc.getLocType()), modulus);
            String enc_sha_currentTime = FileUtil.file_mark(e, modulus, sha_currentTime);
            String enc_sha_locType = FileUtil.file_mark(e, modulus, sha_locType);
            if (!this.isFirstStep && db.checkIsFirstStep(db.getshare())) {
                CountStep.getInstance(context).setStep(0);
                this.isFirstStep = true;
            }

            db.changeIsOnPauseToResume();
            int restV = db.saveAdditionalRecord(enc_sha_latitude, enc_sha_longitude, String.valueOf(CountStep.getInstance(context).getStep()), enc_sha_currentTime, enc_id, enc_sha_locType, type);
            if (restV == -77) {
                int result = -1 / 0;
                return result;
            } else {
                return 1;
            }
        }
    }

    private void monitorRefreshPause() {
        if (this.countdown != null) {
            this.countdown.cancel();
            this.countdown = null;
        }

        this.countdown = (new CountDown(15)).setCallMethodBack(new CountDown.CallMethodBack() {
            public void Do() {
                DataAcquisition.this.pauseMeasureLine();
                DataAcquisition.this.countdown = null;
            }
        });
        Thread aa = new Thread(this.countdown);
        aa.start();
    }

    private boolean check_Init_SharePref(Context context) {
        if (!this.hasUUID_SP) {
            this.SP = context.getSharedPreferences("uuidloc", 0);
            DBManager dbm = DBManager.getInstance(context);
            this.hasUUID_SP = this.SP.getBoolean(dbm.getshare() + "boolean", false);
            return this.hasUUID_SP;
        } else {
            return true;
        }
    }
}
