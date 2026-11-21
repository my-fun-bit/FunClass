package com.myfun.funclass.core.parser

import com.myfun.funclass.core.model.Course
import com.myfun.funclass.core.model.CourseSession
import com.myfun.funclass.core.model.DefaultTimeSchemes
import com.myfun.funclass.core.model.HolidayAdjustType
import com.myfun.funclass.core.model.HolidayAdjustment
import com.myfun.funclass.core.model.ScheduleTable
import com.myfun.funclass.core.model.WeekSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserAndCodecTest {

    @Test
    fun wakeupScheduleCodec_roundTripsIrregularWeeksAndTimeScheme() {
        val scheme = DefaultTimeSchemes.standardCollege(1L)
        val table = ScheduleTable(
            id = 1L,
            name = "2026秋季课表",
            termStartDate = "2026-09-07",
            maxWeeks = 18,
            maxNodes = 12
        )
        val courses = listOf(
            Course(
                id = 1L,
                tableId = 1L,
                name = "编译原理",
                colorHex = "#5B8FF9",
                credit = 3.5f,
                note = "闭卷考试",
                sessions = listOf(
                    CourseSession(
                        id = 1L,
                        courseId = 1L,
                        tableId = 1L,
                        dayOfWeek = 2,
                        startNode = 3,
                        nodeCount = 2,
                        weeks = WeekSet.decode("1-4,6,8-12"),
                        room = "计算机楼401",
                        teacher = "陈教授"
                    )
                )
            )
        )

        val encoded = WakeupScheduleCodec.encode(table, scheme, courses)
        assertEquals(5, encoded.lines().size)

        val decoded = WakeupScheduleCodec.decode(encoded)
        assertEquals("2026秋季课表", decoded.tableName)
        assertEquals("2026-09-07", decoded.termStartDate)
        assertEquals(1, decoded.courses.size)
        val decodedCourse = decoded.courses.first()
        assertEquals("编译原理", decodedCourse.name)
        assertEquals(3.5f, decodedCourse.credit, 0.01f)
        assertEquals(1, decodedCourse.sessions.size)
        assertEquals(
            setOf(1, 2, 3, 4, 6, 8, 9, 10, 11, 12),
            decodedCourse.sessions.first().weeks.weeks
        )
        assertEquals("计算机楼401", decodedCourse.sessions.first().room)
    }

    @Test
    fun academicParsers_parseAllFiveSystems() {
        // 1. ZhengFang JSON
        val zfJson = """
            {"kbList":[{"kcmc":"操作系统","xqj":"1","jcs":"1-2","zcd":"1-16周","cdmc":"主楼202","xm":"王老师","xf":"4.0"}]}
        """.trimIndent()
        val zfRes = ParserRegistry.autoParse(zfJson)
        assertEquals(1, zfRes.courses.size)
        assertEquals("操作系统", zfRes.courses.first().name)
        assertEquals("主楼202", zfRes.courses.first().sessions.first().room)

        // 2. New URP JSON
        val urpJson = """
            {"dateList":[{"selectCourseList":[{"courseName":"线性代数","attendClassTeacher":"李老师*","unit":"3","timeAndPlaceList":[{"classDay":3,"classSessions":5,"continuingSession":2,"teachingBuildingName":"理教","classroomName":"105","classWeek":"1111111100000000"}]}]}]}
        """.trimIndent()
        val urpRes = ParserRegistry.autoParse(urpJson)
        assertEquals(1, urpRes.courses.size)
        assertEquals("线性代数", urpRes.courses.first().name)
        assertEquals((1..8).toSet(), urpRes.courses.first().sessions.first().weeks.weeks)

        // 3. JinZhi JSON
        val jzJson = """
            {"datas":{"xskcb":{"rows":[{"KCM":"计算机网络","SKXQ":4,"KSJC":3,"JSJC":4,"ZCMC":"1-15周(单)","JASMC":"信息楼B301","SKJS":"赵教授"}]}}}
        """.trimIndent()
        val jzRes = ParserRegistry.autoParse(jzJson)
        assertEquals(1, jzRes.courses.size)
        assertTrue(jzRes.courses.first().sessions.first().weeks.isAllOdd())

        // 4. QiangZhi HTML
        val qzHtml = """
            <table id="kbtable"><tr>
              <td><div class="kbcontent">离散数学<br/><font title="老师">孙老师</font><br/><font title="周次(节次)">1-12(周)[01-02节]</font><br/><font title="教室">教B-305</font></div></td>
              <td></td><td></td><td></td><td></td><td></td><td></td>
            </tr></table>
        """.trimIndent()
        val qzRes = ParserRegistry.autoParse(qzHtml)
        assertEquals(1, qzRes.courses.size)
        assertEquals("离散数学", qzRes.courses.first().name)
        assertEquals("教B-305", qzRes.courses.first().sessions.first().room)

        // 5. JS Plugin payload
        val jsPayload = """
            {"funclassPluginVersion":1,"tableName":"自定义脚本课表","courses":[{"name":"人工智能","day":5,"startNode":7,"step":2,"weeks":"1-12","room":"AI实验室","teacher":"周教授"}]}
        """.trimIndent()
        val jsRes = ParserRegistry.autoParse(jsPayload)
        assertEquals("自定义脚本课表", jsRes.tableName)
        assertEquals("人工智能", jsRes.courses.first().name)
    }

    @Test
    fun csvImporterAndIcsExporter_workEndToEnd() {
        val sampleCsv = CsvExcelImporter.sampleTemplateCsv()
        val parsed = CsvExcelImporter.parse(sampleCsv)
        assertEquals(3, parsed.courses.size)
        assertTrue(parsed.warnings.isEmpty())

        val table = ScheduleTable(id = 1L, name = "测试课表", termStartDate = "2026-09-07", maxWeeks = 2)
        val scheme = DefaultTimeSchemes.standardCollege(1L)
        val ics = IcsExporter.export(
            table = table,
            timeScheme = scheme,
            courses = parsed.courses,
            adjustments = listOf(
                HolidayAdjustment(
                    tableId = 1L,
                    targetDate = "2026-09-07",
                    adjustType = HolidayAdjustType.OFF,
                    label = "开学典礼停课"
                )
            )
        )
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("SUMMARY:大学英语"))
        // Sep 7 (Week 1 Monday) was OFF, so 高等数学 should NOT appear on 20260907, only on 20260914
        assertTrue(!ics.contains("DTSTART:20260907T080000"))
        assertTrue(ics.contains("DTSTART:20260914T080000"))

        // Round-trip ICS import back into courses
        val reimported = IcsImporter.parse(ics, timeScheme = scheme)
        assertEquals("测试课表", reimported.tableName)
        assertEquals(3, reimported.courses.size)
        val math = reimported.courses.first { it.name == "高等数学" }
        assertEquals("张教授", math.sessions.first().teacher)
        assertEquals("教A-101", math.sessions.first().room)
        assertEquals(setOf(2), math.sessions.first().weeks.weeks)
    }

    @Test
    fun icsImporter_expandsWakeupRruleOddAndEveryWeekEvents() {
        val wakeupIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Example//ScheduleExporter//EN
            X-WR-CALNAME:第三方ICS导出课表
            BEGIN:VEVENT
            SUMMARY:高等数学 @教A-101
            LOCATION:教A-101
            DESCRIPTION:第 1 - 2 节\n授课教师：张教授
            DTSTART:20260907T080000
            DTEND:20260907T094000
            RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=16
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:物理实验
            LOCATION:实验楼302
            DESCRIPTION:第 5 - 6 节\n授课教师：周老师
            DTSTART:20260908T140000
            DTEND:20260908T154000
            RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=8
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = IcsImporter.parse(wakeupIcs)
        assertEquals("第三方ICS导出课表", result.tableName)
        assertEquals("2026-09-07", result.termStartDate)
        assertEquals(2, result.courses.size)

        val math = result.courses.first { it.name == "高等数学" }
        assertEquals((1..16).toSet(), math.sessions.first().weeks.weeks)
        assertEquals(1, math.sessions.first().startNode)
        assertEquals(2, math.sessions.first().nodeCount)
        assertEquals("张教授", math.sessions.first().teacher)

        val lab = result.courses.first { it.name == "物理实验" }
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15), lab.sessions.first().weeks.weeks)
        assertEquals(5, lab.sessions.first().startNode)
    }

    @Test
    fun icsImporter_handlesMidnightUntilBoundaryOddWeeksAndTeacherSplit() {
        val shanghai = java.time.ZoneId.of("Asia/Shanghai")
        val realWakeupIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Example//ScheduleExporter//EN
            BEGIN:VEVENT
            SUMMARY:信息论基础
            DTSTART;TZID=Asia/Shanghai:20260917T080000
            DTEND;TZID=Asia/Shanghai:20260917T093500
            RRULE:FREQ=WEEKLY;UNTIL=20260923T160000Z;INTERVAL=1
            LOCATION:东区 教A－102 张老师
            DESCRIPTION:第1 - 2节\n东区 教A－102\n张老师
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:信息论基础
            DTSTART;TZID=Asia/Shanghai:20261001T080000
            DTEND;TZID=Asia/Shanghai:20261001T093500
            RRULE:FREQ=WEEKLY;UNTIL=20261007T160000Z;INTERVAL=1
            LOCATION:东区 教A－102 张老师
            DESCRIPTION:第1 - 2节\n东区 教A－102\n张老师
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:通信原理
            DTSTART;TZID=Asia/Shanghai:20260914T153500
            DTEND;TZID=Asia/Shanghai:20260914T171000
            RRULE:FREQ=WEEKLY;UNTIL=20270103T160000Z;INTERVAL=1
            LOCATION:东区 教A－110 李老师
            DESCRIPTION:第8 - 9节\n东区 教A－110\n李老师
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:职业规划
            DTSTART;TZID=Asia/Shanghai:20261001T153500
            DTEND;TZID=Asia/Shanghai:20261001T171000
            RRULE:FREQ=WEEKLY;UNTIL=20261028T160000Z;INTERVAL=1
            LOCATION:东区 教A－101 赵老师
            DESCRIPTION:第8 - 9节\n东区 教A－101\n赵老师
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = IcsImporter.parse(realWakeupIcs, fallbackTableName = "示例学期", zoneId = shanghai)
        assertEquals("2026-09-14", result.termStartDate)
        assertEquals(16, result.maxWeeks)

        val infoTheory = result.courses.first { it.name == "信息论基础" }
        // Must ONLY be odd weeks 1 and 3 (NOT 1,2,3,4!)
        assertEquals(setOf(1, 3), infoTheory.sessions.first().weeks.weeks)
        assertEquals("东区 教A－102", infoTheory.sessions.first().room)
        assertEquals("张老师", infoTheory.sessions.first().teacher)

        val comm = result.courses.first { it.name == "通信原理" }
        // Must be 1..16 (NOT 1..17!)
        assertEquals((1..16).toSet(), comm.sessions.first().weeks.weeks)
        assertEquals("东区 教A－110", comm.sessions.first().room)
        assertEquals("李老师", comm.sessions.first().teacher)

        val career = result.courses.first { it.name == "职业规划" }
        // Must be 3..6 (NOT 3..7!)
        assertEquals((3..6).toSet(), career.sessions.first().weeks.weeks)

        // With overrideTermStartDate shifted 1 week earlier (2026-09-07), all weeks shift +1
        val shifted = IcsImporter.parse(
            realWakeupIcs,
            fallbackTableName = "示例学期",
            zoneId = shanghai,
            overrideTermStartDate = java.time.LocalDate.of(2026, 9, 7)
        )
        assertEquals("2026-09-07", shifted.termStartDate)
        assertEquals((2..17).toSet(), shifted.courses.first { it.name == "通信原理" }.sessions.first().weeks.weeks)
    }

    @Test
    fun icsImporter_supportsByDayAndExDate() {
        val shanghai = java.time.ZoneId.of("Asia/Shanghai")
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:算法设计
            DTSTART;TZID=Asia/Shanghai:20260907T080000
            DTEND;TZID=Asia/Shanghai:20260907T094000
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=6
            EXDATE;TZID=Asia/Shanghai:20260914T080000
            LOCATION:教A-201
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = IcsImporter.parse(ics, zoneId = shanghai)
        val algo = result.courses.first { it.name == "算法设计" }
        val mon = algo.sessions.first { it.dayOfWeek == 1 }
        val wed = algo.sessions.first { it.dayOfWeek == 3 }
        // Mon had weeks 1, 2, 3, minus EXDATE 2026-09-14 (week 2) -> {1, 3}
        assertEquals(setOf(1, 3), mon.weeks.weeks)
        // Wed has weeks 1, 2, 3
        assertEquals(setOf(1, 2, 3), wed.weeks.weeks)
    }

    @Test
    fun otherImporters_fixNonPaddedDateZhengFangWeeksQiangZhiOffsetAndDelimiters() {
        // 1. WeekSet.decode with semicolons, spaces, and global trailing (单)
        assertEquals(setOf(1, 2, 3, 4, 10, 11, 12), WeekSet.decode("1-4周;10-12周").weeks)
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15), WeekSet.decode("1-5,7-15周(单)").weeks)

        // 2. WakeupScheduleCodec with non-zero-padded startDate ("2026-9-7"), sundayFirst, and dummy 00:00 slots
        val legacyWakeup = listOf(
            """{"courseLen":45,"id":1,"name":"默认","sameBreakLen":false,"sameLen":true,"theBreakLen":10}""",
            """[{"endTime":"08:45","node":1,"startTime":"08:00","timeTable":1},{"endTime":"00:00","node":15,"startTime":"00:00","timeTable":1}]""",
            """{"id":1,"maxWeek":18,"nodes":12,"startDate":"2026-9-7","sundayFirst":true,"tableName":"旧版课表","timeTable":1}""",
            """[{"courseName":"信号与系统","id":1,"tableId":1}]""",
            """[{"day":1,"endWeek":16,"id":1,"startNode":1,"startWeek":1,"step":2,"tableId":1,"type":0}]"""
        ).joinToString("\n")
        val decodedWakeup = WakeupScheduleCodec.decode(legacyWakeup)
        assertEquals("2026-09-07", decodedWakeup.termStartDate)
        assertTrue(decodedWakeup.sundayFirst)
        assertEquals(1, decodedWakeup.timeScheme!!.regularSlots().size)

        // 3. New ZhengFang HTML "(1-2节)1-16周" must NOT parse node 1-2 as weeks
        val newZfHtml = """
            <table id="kbgrid_table_0"><tr>
              <td id="1-1" class="td_wrap">
                <div class="timetable_con">
                  <span class="title">编译原理</span>
                  <p>(1-2节)1-16周</p>
                  <p>教室：教A-203</p>
                  <p>教师：陈老师</p>
                </div>
              </td>
            </tr></table>
        """.trimIndent()
        val newZfRes = ZhengFangParser().parse(newZfHtml)
        assertEquals((1..16).toSet(), newZfRes.courses.first().sessions.first().weeks.weeks)
        assertEquals(1, newZfRes.courses.first().sessions.first().startNode)
        assertEquals(2, newZfRes.courses.first().sessions.first().nodeCount)

        // 4. Classic ZhengFang #Table1 "周二第3,4节{第2-16周|双周}"
        val classicZfHtml = """
            <table id="Table1"><tr>
              <td>第3节</td><td></td>
              <td rowspan="2">概率论<br/>周二第3,4节{第2-16周|双周}<br/>李老师<br/>教B-102</td>
              <td></td><td></td><td></td><td></td><td></td>
            </tr></table>
        """.trimIndent()
        val classicZfRes = ZhengFangParser().parse(classicZfHtml)
        assertEquals(setOf(2, 4, 6, 8, 10, 12, 14, 16), classicZfRes.courses.first().sessions.first().weeks.weeks)

        // 5. QiangZhi #kbtable with leading <td> node column (8 tds in row)
        val qz8ColHtml = """
            <table id="kbtable"><tr>
              <td>第一大节</td>
              <td><div class="kbcontent">微积分<br/><font title="老师">王老师</font><br/><font title="周次(节次)">1-16(周)[01-02节]</font><br/><font title="教室">教1-101</font></div></td>
              <td></td><td></td><td></td><td></td><td></td><td></td>
            </tr></table>
        """.trimIndent()
        val qz8Res = QiangZhiParser().parse(qz8ColHtml)
        assertEquals(1, qz8Res.courses.first().sessions.first().dayOfWeek)

        // 6. CSV with "第13节".."第14节" adapts maxNodes to 14
        val csv = "课程名称,星期,开始节数,结束节数,老师,地点,周数\n晚间选修,2,第13节,第14节,张老师,教C-301,1-16周"
        val csvRes = CsvExcelImporter.parse(csv)
        assertEquals(13, csvRes.courses.first().sessions.first().startNode)
        assertEquals(14, csvRes.maxNodes)
    }

    @Test
    fun genericHtmlParser_skipsWeekdayHeadersTracksRowspanAndCleansCourseMeta() {
        val html = """
            <html><body>
            <table>
              <tr><td>系统导航栏</td><td>我的课表</td></tr>
            </table>
            <table>
              <tr>
                <td>时间</td>
                <td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td><td>星期五</td><td>星期六</td><td>星期日</td>
              </tr>
              <tr>
                <td>第1节</td>
                <td></td>
                <td rowspan="2"><p>嵌入式系统</p><p>1-16周</p><p>周老师</p><p>教4-15</p></td>
                <td></td><td></td><td></td><td></td><td></td>
              </tr>
              <tr>
                <td>第2节</td>
                <td></td>
                <td><div>微电子器件[1-16周]</div><div>教3-201</div><div>张老师</div></td>
                <td></td><td></td><td></td><td></td>
              </tr>
              <tr>
                <td>第3节</td>
                <td></td>
                <td rowspan="2"><p>半导体物理[03-04节]</p><p>1-12周(单)</p><p>实验楼B102</p></td>
                <td></td><td></td><td></td><td></td><td></td>
              </tr>
              <tr><td>第4节</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            </table>
            </body></html>
        """.trimIndent()

        val result = GenericHtmlTableParser().parse(html)
        val names = result.courses.map { it.name }.toSet()
        assertFalse("星期二" in names)
        assertFalse("星期三" in names)
        assertFalse("星期日" in names)
        assertEquals(setOf("嵌入式系统", "微电子器件", "半导体物理"), names)

        val embedded = result.courses.first { it.name == "嵌入式系统" }.sessions.first()
        assertEquals(2, embedded.dayOfWeek)
        assertEquals(1, embedded.startNode)
        assertEquals(2, embedded.nodeCount)
        assertEquals((1..16).toSet(), embedded.weeks.weeks)
        assertEquals("教4-15", embedded.room)
        assertEquals("周老师", embedded.teacher)

        val device = result.courses.first { it.name == "微电子器件" }.sessions.first()
        assertEquals(3, device.dayOfWeek)
        assertEquals(2, device.startNode)
        assertEquals(1, device.nodeCount)
        assertEquals((1..16).toSet(), device.weeks.weeks)
        assertEquals("教3-201", device.room)
        assertEquals("张老师", device.teacher)

        val semi = result.courses.first { it.name == "半导体物理" }.sessions.first()
        assertEquals(2, semi.dayOfWeek)
        assertEquals(3, semi.startNode)
        assertEquals(2, semi.nodeCount)
        assertEquals(setOf(1, 3, 5, 7, 9, 11), semi.weeks.weeks)
        assertEquals("实验楼B102", semi.room)
    }

    @Test
    fun zhengFangClassicTable1_handlesMorningRowspanAndMultiCourseCells() {
        val html = """
            <table id="Table1">
              <tr>
                <td colspan="2">时间</td>
                <td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td><td>星期五</td><td>星期六</td><td>星期日</td>
              </tr>
              <tr>
                <td colspan="2">早晨</td><td></td><td></td><td></td><td></td><td></td><td></td><td></td>
              </tr>
              <tr>
                <td rowspan="4">上午</td>
                <td>第1节</td>
                <td rowspan="2">高等数学<br/>周一第1,2节{第1-16周}<br/>李教授<br/>一教101<br/><br/>线性代数<br/>周一第1,2节{第2-16周|双周}<br/>王老师<br/>二教202</td>
                <td></td><td></td><td></td><td></td><td></td><td></td>
              </tr>
              <tr>
                <td>第2节</td>
                <td></td>
                <td rowspan="2">大学物理<br/>周三第2,3节{第1-12周|单周}<br/>陈老师<br/>理教301</td>
                <td></td><td></td><td></td><td></td>
              </tr>
              <tr><td>第3节</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
              <tr><td>第4节</td><td></td><td></td><td></td><td></td><td></td><td></td><td></td></tr>
            </table>
        """.trimIndent()

        val res = ZhengFangParser().parse(html)
        val math = res.courses.first { it.name == "高等数学" }.sessions.first()
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startNode)
        assertEquals(2, math.nodeCount)
        assertEquals((1..16).toSet(), math.weeks.weeks)
        assertEquals("一教101", math.room)
        assertEquals("李教授", math.teacher)

        val linalg = res.courses.first { it.name == "线性代数" }.sessions.first()
        assertEquals(1, linalg.dayOfWeek)
        assertEquals(1, linalg.startNode)
        assertEquals(setOf(2, 4, 6, 8, 10, 12, 14, 16), linalg.weeks.weeks)
        assertEquals("二教202", linalg.room)

        val physics = res.courses.first { it.name == "大学物理" }.sessions.first()
        assertEquals(3, physics.dayOfWeek)
        assertEquals(2, physics.startNode)
        assertEquals(2, physics.nodeCount)
        assertEquals(setOf(1, 3, 5, 7, 9, 11), physics.weeks.weeks)
        assertEquals("理教301", physics.room)
    }

    @Test
    fun newZhengFangInsideFrameSplit_parsesSemanticTitleAttributes() {
        val html = """
            <table><tr><td>外层导航框架</td></tr></table>
            <!--FRAME_SPLIT-->
            <table id="kbgrid_table_0">
              <tr>
                <td id="3-5" class="td_wrap" rowspan="2">
                  <div class="timetable_con">
                    <u class="title">FPGA逻辑设计</u>
                    <p><span title="节/周"></span>(5-6节)1-16周</p>
                    <p><span title="上课地点"></span>信息楼A402</p>
                    <p><span title="教师"></span>刘老师</p>
                  </div>
                </td>
              </tr>
            </table>
        """.trimIndent()

        val res = ParserRegistry.autoParse(html)
        assertEquals(1, res.courses.size)
        val fpga = res.courses.first()
        assertEquals("FPGA逻辑设计", fpga.name)
        val s = fpga.sessions.first()
        assertEquals(3, s.dayOfWeek)
        assertEquals(5, s.startNode)
        assertEquals(2, s.nodeCount)
        assertEquals((1..16).toSet(), s.weeks.weeks)
        assertEquals("信息楼A402", s.room)
        assertEquals("刘老师", s.teacher)
    }

    @Test
    fun qiangZhiAndKingosoft_skipWeekdayHeaderRowsWithoutShiftingNodes() {
        val qzHtml = """
            <table><tr><td>外层壳表格</td></tr></table>
            <!--FRAME_SPLIT-->
            <table id="kbtable">
              <tr>
                <td>节次</td><td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td><td>星期五</td><td>星期六</td><td>星期日</td>
              </tr>
              <tr>
                <td>第一大节</td>
                <td><div class="kbcontent">信号处理<br/><font title="老师">吴老师</font><br/><font title="周次(节次)">1-16(周)[01-02节]</font><br/><font title="教室">教2-301</font></div></td>
                <td></td><td></td><td></td><td></td><td></td><td></td>
              </tr>
            </table>
        """.trimIndent()
        val qzRes = QiangZhiParser().parse(qzHtml)
        assertEquals(1, qzRes.courses.size)
        assertEquals(1, qzRes.courses.first().sessions.first().startNode)
        assertEquals("教2-301", qzRes.courses.first().sessions.first().room)

        val kgHtml = """
            <table id="kcb_table">
              <tr>
                <td>节次</td><td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td><td>星期五</td><td>星期六</td><td>星期日</td>
              </tr>
              <tr>
                <td>1-2节</td>
                <td></td>
                <td>数字电路<br/>赵老师<br/>1-14周<br/>东配楼108</td>
                <td></td><td></td><td></td><td></td><td></td>
              </tr>
            </table>
        """.trimIndent()
        val kgRes = KingosoftParser().parse(kgHtml)
        assertEquals(1, kgRes.courses.size)
        val session = kgRes.courses.first().sessions.first()
        assertEquals(2, session.dayOfWeek)
        assertEquals(1, session.startNode)
        assertEquals(2, session.nodeCount)
        assertEquals((1..14).toSet(), session.weeks.weeks)
        assertEquals("东配楼108", session.room)
        assertEquals("赵老师", session.teacher)
    }

    @Test
    fun genericHtmlParser_parsesLeadingNodeWithClockTimeAndChineseEleventhNode() {
        val html = """
            <table>
              <tr>
                <td>节次</td><td>星期一</td><td>星期二</td><td>星期三</td><td>星期四</td><td>星期五</td><td>星期六</td><td>星期日</td>
              </tr>
              <tr>
                <td>第1-2节<br/>08:00-09:40</td>
                <td>高等数学<br/>1-16周<br/>教1-102<br/>王老师</td>
                <td></td><td></td><td></td><td></td><td></td><td></td>
              </tr>
              <tr>
                <td>第十一节<br/>19:00-19:45</td>
                <td></td>
                <td>形势与政策<br/>1-8周<br/>礼堂</td>
                <td></td><td></td><td></td><td></td><td></td>
              </tr>
            </table>
        """.trimIndent()

        val res = GenericHtmlTableParser().parse(html)
        val math = res.courses.first { it.name == "高等数学" }.sessions.first()
        assertEquals(1, math.dayOfWeek)
        assertEquals(1, math.startNode)
        assertEquals(2, math.nodeCount)

        val politics = res.courses.first { it.name == "形势与政策" }.sessions.first()
        assertEquals(2, politics.dayOfWeek)
        assertEquals(11, politics.startNode)
        assertEquals(1, politics.nodeCount)
    }
}
