package com.jeffheaton.dissertation.util;

import org.encog.ml.data.MLDataSet;
import org.encog.util.csv.CSVFormat;
import org.encog.util.csv.ReadCSV;

import java.io.File;
import java.io.InputStream;

/**
 * Created by jeff on 4/11/16.
 */
public class QuickEncodeDataset {

    private double[] min;
    private double[] max;
    private double[] mean;
    private double[] sdev;
    private boolean[] numeric;
    private String[] name;
    private int[] rowCount;

    private boolean headers;
    private CSVFormat format;
    private int targetColumn;
    private ObtainInputStream streamSource;

    private boolean isNA(String str) {
        if(str.trim().equalsIgnoreCase("NA")|| str.trim().equals("?")) {
            return true;
        } else {
            return false;
        }
    }


    private void processPass1() {
        InputStream stream = this.streamSource.obtain();
        ReadCSV csv = new ReadCSV(stream,headers,format);
        int count = 0;

        while(csv.next()) {
            if( this.min==null) {
                count = csv.getColumnCount();
                this.min = new double[count];
                this.max = new double[count];
                this.mean = new double[count];
                this.sdev = new double[count];
                this.numeric = new boolean[count];
                this.name = new String[count];
                this.rowCount = new int[count];
                for(int i=0;i<this.numeric.length;i++) {
                    this.numeric[i]=true;
                    this.min[i]=Double.POSITIVE_INFINITY;
                    if( this.headers ) {
                        this.name[i] = csv.getColumnNames().get(i);
                    } else {
                        this.name[i] = "field-"+i;
                    }
                }
            }

            for(int i=0;i<count;i++) {
                if(!this.numeric[i]) {
                    continue;
                }

                try {
                    if(!isNA(csv.get(i))) {
                        double d = Double.parseDouble(csv.get(i));
                        this.min[i] = Math.min(d, this.min[i]);
                        this.max[i] = Math.max(d, this.max[i]);
                        this.mean[i] += d;
                        this.rowCount[i]++;
                    }
                } catch (NumberFormatException ex) {
                    this.numeric[i] = false;
                }
            }
        }
        csv.close();
        for(int i=0;i<this.mean.length;i++) {
            if(this.numeric[i]) {
                this.mean[i] /= this.rowCount[i];
            }
        }
    }

    private void processPass2() {
        InputStream stream = this.streamSource.obtain();
        ReadCSV csv = new ReadCSV(stream,headers,format);

        while(csv.next()) {
            for(int i=0;i<this.numeric.length;i++) {
                if(this.numeric[i]) {
                    String str = csv.get(i);
                    if( !isNA(str)) {
                        double d = Double.parseDouble(str)-this.mean[i];
                        this.sdev[i] += d*d;
                    }
                }
            }
        }
        csv.close();
        for(int i=0;i<this.mean.length;i++) {
            if(this.numeric[i]) {
                this.sdev[i] /= this.rowCount[i];
            }
        }
    }

    private MLDataSet processPass3() {
        int inputFeatureCount = 0;
        for(int i=0;i<this.numeric.length;i++) {
            if(this.numeric[i]) {
                inputFeatureCount++;
            }
        }
        int[] inputFeatures = new int[inputFeatureCount-1];
        int idx = 0;
        for(int i=0;i<this.numeric.length;i++) {
            if(this.numeric[i] && i!=this.targetColumn) {
                inputFeatures[idx++] = i;
            }
        }
        int[] targetFeatures = new int[1];

        InputStream stream = this.streamSource.obtain();
        ReadCSV csv = new ReadCSV(stream,headers,format);

        MLDataSet result = Loader.loadCSV(csv,inputFeatures,targetFeatures);
        csv.close();
        return result;
    }

    public void dumpFieldInfo() {
        for(int i=0;i<this.name.length;i++) {
            if( this.numeric[i] ) {
                System.out.println(i + ":" + this.name[i] + ",min=" + this.min[i] + ",max=" + this.max[i] + ",mean=" + this.mean[i]+",sdev="+this.sdev[i]);
            } else {
                System.out.println(i + ":" + this.name[i] + "(ignored)");
            }
        }
    }


    public MLDataSet process(ObtainInputStream theStreamSource, int targetColumn, boolean theHeaders, CSVFormat theFormat) {
        this.streamSource = theStreamSource;
        this.headers = theHeaders;
        this.format = theFormat;
        processPass1();
        processPass2();
        return processPass3();
    }

    private boolean skip(int n) {
        return !this.numeric[n] || n==0 || n==1;
    }

    public double[] getMin() {
        return min;
    }

    public double[] getMax() {
        return max;
    }

    public double[] getMean() {
        return mean;
    }

    public double[] getSdev() {
        return sdev;
    }

    public boolean[] getNumeric() {
        return numeric;
    }

    public String[] getName() {
        return name;
    }

    public int[] getRowCount() {
        return rowCount;
    }

    public int getTargetColumn() {
        return targetColumn;
    }
}