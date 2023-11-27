package com.xayc.dataacquisition;

import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

class MetaData {
    public static MetaData metaData;
    public String setUpDate;
    private String a = "";
    private long b = 0L;

    private MetaData() {
    }

    public static MetaData getInstance() {
        if (metaData == null) {
            metaData = new MetaData();
        }

        return metaData;
    }

    public boolean isInited() {
        return !TextUtils.isEmpty(this.a) && this.b != 0L;
    }

    public long getPublicE() {
        return this.b;
    }

    public String getModulus() {
        return this.a;
    }

    public void setPublicEAndModulus(long publie_e, String modulus) {
        this.b = publie_e;
        this.a = modulus;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        this.setUpDate = simpleDateFormat.format(new Date());
    }

    public boolean isTodaysData() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String today = simpleDateFormat.format(new Date());
        return today.equals(this.setUpDate);
    }
}
