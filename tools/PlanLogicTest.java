package com.rmstock.nativeapp;
import java.util.*;
public class PlanLogicTest {
  static int fails=0;
  static void eq(String n,Object a,Object b){ if(!String.valueOf(a).equals(String.valueOf(b))){fails++;System.out.println("FAIL "+n+": got "+a+" expected "+b);} else System.out.println("ok   "+n+" = "+a); }
  public static void main(String[] x){
    eq("valid",PlanLogic.validDate("2026-02-28"),true);
    eq("feb29 non-leap",PlanLogic.validDate("2026-02-29"),false);
    eq("feb29 leap",PlanLogic.validDate("2028-02-29"),true);
    eq("short form",PlanLogic.validDate("2026-1-5"),false);
    eq("month 13",PlanLogic.validDate("2026-13-01"),false);
    eq("empty",PlanLogic.validDate(""),false);
    eq("null",PlanLogic.validDate(null),false);
    eq("shift -7",PlanLogic.shiftDate("2026-03-03",-7),"2026-02-24");
    eq("shift year",PlanLogic.shiftDate("2026-01-02",-7),"2025-12-26");
    eq("between",PlanLogic.daysBetween("2026-03-03","2026-03-10"),7);
    eq("between neg",PlanLogic.daysBetween("2026-03-03","2026-02-28"),-3);
    eq("between year",PlanLogic.daysBetween("2025-12-30","2026-01-02"),3);
    eq("daysLeft",PlanLogic.daysLeft(30,10),3.0);
    eq("daysLeft none",PlanLogic.daysLeft(30,0),-1.0);
    eq("daysLeft neg stock",PlanLogic.daysLeft(-5,10),0.0);
    eq("status neg",PlanLogic.status(-1,0,0),PlanLogic.NEG);
    eq("status low",PlanLogic.status(50,50,1),PlanLogic.LOW);
    eq("status soon",PlanLogic.status(20,5,10),PlanLogic.SOON);
    eq("status ok",PlanLogic.status(100,5,10),PlanLogic.OK);
    eq("status ok no use",PlanLogic.status(100,0,0),PlanLogic.OK);
    List<String[]> rec=new ArrayList<>();
    rec.add(new String[]{"101","301","25"});rec.add(new String[]{"101","302","10"});rec.add(new String[]{"103","301","5"});rec.add(new String[]{"104","302","0"});
    Map<String,Double> b=new HashMap<>();b.put("301",2.0);b.put("302",3.0);
    eq("need",PlanLogic.need(b,rec),"{101=80.0, 103=10.0}");
    Map<String,Double> st=new HashMap<>();st.put("101",100.0);st.put("103",12.0);
    List<String[]> p301=new ArrayList<>();p301.add(rec.get(0));p301.add(rec.get(2));
    eq("max301",PlanLogic.maxBatches(st,p301),2.0);
    eq("max none",PlanLogic.maxBatches(st,new ArrayList<String[]>()),-1.0);
    st.put("103",-4.0);
    eq("max neg stock",PlanLogic.maxBatches(st,p301),0.0);
    System.out.println(fails==0?"ALL PASS":"FAILS: "+fails);
    if(fails>0)System.exit(1);
  }
}
