package com.whisper.wowreader;

final class WholeBookPageModel {
    private int[] counts = new int[0];
    private int known = 0;
    void reset(int chapters){ counts=new int[Math.max(0,chapters)]; known=0; }
    int chapterCount(){ return counts.length; }
    void setChapterCount(int index,int count){
        if(index<0||index>=counts.length)return;int clean=Math.max(1,count);if(counts[index]<=0)known++;counts[index]=clean;
    }
    boolean isComplete(){ return counts.length>0&&known==counts.length; }
    int wholePageIfKnown(int chapter,int page){
        if(chapter<0||chapter>=counts.length)return -1;long prefix=0;for(int i=0;i<chapter;i++){if(counts[i]<=0)return -1;prefix+=counts[i];if(prefix>Integer.MAX_VALUE)return Integer.MAX_VALUE;}
        if(counts[chapter]<=0)return -1;long value=prefix+Math.max(1,Math.min(page,counts[chapter]));return value>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)value;
    }
    int totalPagesIfComplete(){
        if(!isComplete())return -1;long total=0;for(int c:counts){total+=c;if(total>Integer.MAX_VALUE)return Integer.MAX_VALUE;}return (int)total;
    }
}
