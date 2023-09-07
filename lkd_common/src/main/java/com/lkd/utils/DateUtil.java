package com.lkd.utils;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DateUtil{

    /**
     * 获取当前时间的季度信息
     * @param dateTime
     * @return
     */
    public static Season getSeason(LocalDateTime dateTime){
        int firstMonth = dateTime.getMonth().firstMonthOfQuarter().getValue();
        int lastMonth = firstMonth + 2;
        LocalDateTime start = LocalDateTime.of(dateTime.getYear(),firstMonth,1,0,0,0);
        Season s = new Season();
        s.setStartDate(start);
        LocalDateTime end = LocalDateTime.of(dateTime.getYear(),lastMonth,1,0,0,0);
        end = end.plusMonths(1).plusDays(-1);
        s.setEndDate(end);

        return s;
    }

    /**
     * 季节
     */
    @Data
    public static class Season{
        private LocalDateTime startDate;
        private LocalDateTime endDate;
    }



    public static List<LocalDate> datesUntil(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate currentDate = start;

        while (!currentDate.isAfter(end)) {
            dates.add(currentDate);
            currentDate = currentDate.plusDays(1);
        }

        return dates;
    }
}
