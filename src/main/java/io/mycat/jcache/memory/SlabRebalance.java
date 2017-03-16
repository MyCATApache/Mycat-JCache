package io.mycat.jcache.memory;

import java.io.Serializable;


public class SlabRebalance implements Serializable {

    private long slabStart;
    private long slabEnd;
    private long slabPos;
    private int sClsid;
    private int dClsid;
    private int busyItems;
    private int rescues;
    private int evictionsNomem;
    private int inlineReclaim;
    private int chunkRescues;
    private int done;

    public long getSlabStart() {
        return slabStart;
    }

    public void setSlabStart(long slabStart) {
        this.slabStart = slabStart;
    }

    public long getSlabEnd() {
        return slabEnd;
    }

    public void setSlabEnd(long slabEnd) {
        this.slabEnd = slabEnd;
    }

    public long getSlabPos() {
        return slabPos;
    }

    public void setSlabPos(long slabPos) {
        this.slabPos = slabPos;
    }

    public int getsClsid() {
        return sClsid;
    }

    public void setsClsid(int sClsid) {
        this.sClsid = sClsid;
    }

    public int getdClsid() {
        return dClsid;
    }

    public void setdClsid(int dClsid) {
        this.dClsid = dClsid;
    }

    public int getBusyItems() {
        return busyItems;
    }

    public void setBusyItems(int busyItems) {
        this.busyItems = busyItems;
    }

    public int getRescues() {
        return rescues;
    }

    public void setRescues(int rescues) {
        this.rescues = rescues;
    }

    public int getEvictionsNomem() {
        return evictionsNomem;
    }

    public void setEvictionsNomem(int evictionsNomem) {
        this.evictionsNomem = evictionsNomem;
    }

    public int getInlineReclaim() {
        return inlineReclaim;
    }

    public void setInlineReclaim(int inlineReclaim) {
        this.inlineReclaim = inlineReclaim;
    }

    public int getChunkRescues() {
        return chunkRescues;
    }

    public void setChunkRescues(int chunkRescues) {
        this.chunkRescues = chunkRescues;
    }

    public int getDone() {
        return done;
    }

    public void setDone(int done) {
        this.done = done;
    }
}
