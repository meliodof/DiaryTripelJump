# -*- coding: utf-8 -*-
"""Единый документ об исследованиях ИИ в проекте «ДневникТройного» (D:\\диплом).
Запуск: python gen_ai_doc.py <out.docx> <eval_all.json> <gc_result.json> <assistant_eval.json>
Ссылки в тексте пишутся как [@ключ] и при сборке заменяются номерами из списка литературы."""
import sys, json, re
from docx import Document
from docx.shared import Pt, Mm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

OUT = sys.argv[1]
EV = json.load(open(sys.argv[2], encoding="utf-8"))
GC = json.load(open(sys.argv[3], encoding="utf-8"))
AS = json.load(open(sys.argv[4], encoding="utf-8"))   # results/assistant_eval.json
UPDATED = "13.11.2026"
A, B = EV["A"], EV["B"]


def m(x, d=3):
    return f"{x:.{d}f}".replace(".", ",")


def pc(x, d=0):
    return f"{x * 100:.{d}f}".replace(".", ",") + " %"


# ------------------------------------------------------------------ литература (порядок = номер)
REFS = [
    ("mazz", "Mazzaufo C., Beretta M., Matarazzo A., Pavei G., Serranò S., La Torre A., Boccia G. Specific Quintuple: "
     "An On-Field Test to Predict Triple Jump Performance with High Accuracy // Journal of Human Kinetics. – 2025. – "
     "DOI: 10.5114/jhk/205292."),
    ("hud", "Hudgins B., Scharfenberg J., Triplett N. T., McBride J. M. Relationship between jumping ability and running "
     "performance in events of varying distance // Journal of Strength and Conditioning Research. – 2013. – Vol. 27, "
     "No. 3. – P. 563–567."),
    ("lin", "Lin J., Shen J., Zhang J., Zhou A., Guo W. Correlations between horizontal jump and sprint acceleration and "
     "maximal speed performance: a systematic review and meta-analysis // PeerJ. – 2023. – Vol. 11. – e14650. – "
     "DOI: 10.7717/peerj.14650."),
    ("wag", "Wagner C. M., Brauner T., Warneke K., Stefer T., Kuhn L., Hoffmeister M., Wirth K., Keiner M. Absolute and "
     "relative maximum strength measures show differences in their correlations with sprint and jump performances in "
     "trained youth soccer players // Montenegrin Journal of Sports Science and Medicine. – 2023. – Vol. 19, No. 1. – "
     "DOI: 10.26773/mjssm.230309."),
    ("ecss", "Performance profiles & peak age of adolescent triple jump contestants at Youth Olympic Games : тезисы // "
     "28th Annual Congress of the European College of Sport Science (ECSS 2023). – Paris, 2023. – "
     "URL: https://lida.sport-iat.de/dlv/Record/4086266"),
    ("bocc", "Boccia G., Cardinale M., Brustio P. R. Performance progression of elite jumpers: early performances do not "
     "predict later success // Scandinavian Journal of Medicine & Science in Sports. – 2021. – Vol. 31, No. 1. – "
     "P. 132–139. – DOI: 10.1111/sms.13819."),
    ("hop", "Hopkins W. G. Competitive performance of elite track-and-field athletes: variability and smallest worthwhile "
     "enhancements // Sportscience. – 2005. – Vol. 9. – P. 17–20. – URL: https://sportsci.org/jour/05/wghtrack.htm"),
    ("wind", "Moinat M., Fabius O., Emanuel K. S. Data-driven quantification of the effect of wind on athletics "
     "performance // European Journal of Sport Science. – 2018. – Vol. 18, No. 9. – P. 1185–1190."),
    ("bosq", "Bosquet L., Montpetit J., Arvisais D., Mujika I. Effects of tapering on performance: a meta-analysis // "
     "Medicine & Science in Sports & Exercise. – 2007. – Vol. 39, No. 8. – P. 1358–1365."),
    ("muj", "Mujika I., Padilla S. Scientific bases for precompetition tapering strategies // Medicine & Science in "
     "Sports & Exercise. – 2003. – Vol. 35, No. 7. – P. 1182–1187."),
    ("ban", "Banister E. W., Calvert T. W., Savage M. V., Bach T. A systems model of training for athletic performance // "
     "Australian Journal of Sports Medicine. – 1975. – Vol. 7. – P. 57–61."),
    ("hel", "Hellard P., Avalos M., Lacoste L., Barale F., Chatard J.-C., Millet G. P. Assessing the limitations of the "
     "Banister model in monitoring training // Journal of Sports Sciences. – 2006. – Vol. 24, No. 5. – P. 509–520. – "
     "DOI: 10.1080/02640410500244697."),
    ("imb", "Imbach F., Sutton-Charani N., Montmain J., Candau R., Perrey S. Training load responses modelling and model "
     "generalisation in elite sports // Scientific Reports. – 2022. – Vol. 12. – Art. 1586. – "
     "DOI: 10.1038/s41598-022-05392-8."),
    ("gc", "GoldenCheetah OpenData Project : открытый набор обезличенных журналов тренировок [Электронный ресурс] // "
     "Open Science Framework. – URL: https://osf.io/6hfpz/ (дата обращения: 13.11.2026)."),
    ("tfrrs", "TFRRS – Track & Field Results Reporting System : [сайт]. – URL: https://www.tfrrs.org/ "
     "(дата обращения: 13.11.2026)."),
    ("wa", "World Athletics. Athletes profiles and toplists : [сайт]. – URL: https://worldathletics.org/ "
     "(дата обращения: 13.11.2026)."),
    ("bh", "Benjamini Y., Hochberg Y. Controlling the false discovery rate: a practical and powerful approach to multiple "
     "testing // Journal of the Royal Statistical Society. Series B. – 1995. – Vol. 57, No. 1. – P. 289–300."),
    ("phip", "Phipson B., Smyth G. K. Permutation P-values should never be zero: calculating exact P-values when "
     "permutations are randomly drawn // Statistical Applications in Genetics and Molecular Biology. – 2010. – "
     "Vol. 9, No. 1. – Art. 39."),
    ("good", "Good P. Permutation, Parametric and Bootstrap Tests of Hypotheses. – 3rd ed. – New York : Springer, 2005. – 315 p."),
    ("igl", "Iglewicz B., Hoaglin D. C. How to Detect and Handle Outliers. – Milwaukee : ASQC Quality Press, 1993. – 87 p."),
    ("gab", "Gabbett T. J. The training–injury prevention paradox: should athletes be training smarter and harder? // "
     "British Journal of Sports Medicine. – 2016. – Vol. 50, No. 5. – P. 273–280."),
    ("hul", "Hulin B. T., Gabbett T. J., Lawson D. W., Caputi P., Sampson J. A. The acute:chronic workload ratio predicts "
     "injury: high chronic workload may decrease injury risk in elite rugby league players // British Journal of Sports "
     "Medicine. – 2016. – Vol. 50, No. 4. – P. 231–236."),
    ("imp", "Impellizzeri F. M., Tenan M. S., Kempton T., Novak A., Coutts A. J. Acute:chronic workload ratio: conceptual "
     "issues and fundamental pitfalls // International Journal of Sports Physiology and Performance. – 2020. – "
     "Vol. 15, No. 6. – P. 907–913."),
    ("hyn", "Hyndman R. J., Athanasopoulos G. Forecasting: principles and practice. – 3rd ed. – Melbourne : OTexts, 2021. – "
     "URL: https://otexts.com/fpp3/"),
    ("gard", "Gardner E. S. Exponential smoothing: the state of the art – Part II // International Journal of Forecasting. – "
     "2006. – Vol. 22, No. 4. – P. 637–666."),
    ("tash", "Tashman L. J. Out-of-sample tests of forecasting accuracy: an analysis and review // International Journal "
     "of Forecasting. – 2000. – Vol. 16, No. 4. – P. 437–450."),
    ("hub", "Huber P. J. Robust estimation of a location parameter // Annals of Mathematical Statistics. – 1964. – "
     "Vol. 35, No. 1. – P. 73–101."),
    ("skl", "Pedregosa F., Varoquaux G., Gramfort A. et al. Scikit-learn: machine learning in Python // Journal of Machine "
     "Learning Research. – 2011. – Vol. 12. – P. 2825–2830."),
    ("box", "Box G. E. P., Jenkins G. M., Reinsel G. C., Ljung G. M. Time Series Analysis: Forecasting and Control. – "
     "5th ed. – Hoboken : Wiley, 2015. – 712 p."),
    ("gran", "Granger C. W. J., Newbold P. Spurious regressions in econometrics // Journal of Econometrics. – 1974. – "
     "Vol. 2, No. 2. – P. 111–120."),
    ("mol", "Molenaar P. C. M. A manifesto on psychology as idiographic science: bringing the person back into scientific "
     "psychology, this time forever // Measurement: Interdisciplinary Research and Perspectives. – 2004. – Vol. 2, "
     "No. 4. – P. 201–218."),
    ("fish", "Fisher A. J., Medaglia J. D., Jeronimus B. F. Lack of group-to-individual generalizability is a threat to "
     "human subjects research // Proceedings of the National Academy of Sciences. – 2018. – Vol. 115, No. 27. – "
     "P. E6106–E6115."),
    ("gel", "Gelman A., Hill J. Data Analysis Using Regression and Multilevel/Hierarchical Models. – Cambridge : "
     "Cambridge University Press, 2007. – 625 p."),
    ("spy", "Spyropoulou M., Hopker J., Griffin J. E. Modelling intra-season performance trajectories in elite athletics : "
     "препринт // arXiv. – 2024. – arXiv:2405.17214."),
    ("mcm", "McMahan B., Moore E., Ramage D., Hampson S., Agüera y Arcas B. Communication-efficient learning of deep "
     "networks from decentralized data // Proceedings of the 20th International Conference on Artificial Intelligence "
     "and Statistics (AISTATS). – 2017. – P. 1273–1282."),
    ("bpose", "Bazarevsky V., Grishchenko I., Raveendran K., Zhu T., Zhang F., Grundmann M. BlazePose: on-device "
     "real-time body pose tracking : препринт // arXiv. – 2020. – arXiv:2006.10204."),
    ("hay", "Hay J. G. The biomechanics of the triple jump: a review // Journal of Sports Sciences. – 1992. – Vol. 10, "
     "No. 4. – P. 343–378."),
    ("gk4", "Гражданский кодекс Российской Федерации. Часть четвёртая : Федеральный закон от 18.12.2006 № 230-ФЗ "
     "(ред. действующая) // Собрание законодательства РФ. – 2006. – № 52 (ч. 1). – Ст. 5496."),
    ("pd", "О персональных данных : Федеральный закон от 27.07.2006 № 152-ФЗ (ред. действующая) // Собрание "
     "законодательства РФ. – 2006. – № 31 (ч. 1). – Ст. 3451."),
    ("med", "Об основах охраны здоровья граждан в Российской Федерации : Федеральный закон от 21.11.2011 № 323-ФЗ "
     "(ред. действующая) // Собрание законодательства РФ. – 2011. – № 48. – Ст. 6724."),
    ("u490", "О развитии искусственного интеллекта в Российской Федерации : Указ Президента РФ от 10.10.2019 № 490 "
     "(ред. от 15.02.2024) // Собрание законодательства РФ. – 2019. – № 41. – Ст. 5700."),
    ("ethic", "Кодекс этики в сфере искусственного интеллекта / Альянс в сфере искусственного интеллекта. – М., 2021. – "
     "URL: https://ethics.a-ai.ru/ (дата обращения: 13.11.2026)."),
    ("gost", "ГОСТ Р 59276-2020. Системы искусственного интеллекта. Способы обеспечения доверия. Общие положения. – "
     "М. : Стандартинформ, 2021."),
    ("apache", "Apache License, Version 2.0 / The Apache Software Foundation. – 2004. – "
     "URL: https://www.apache.org/licenses/LICENSE-2.0 (дата обращения: 13.11.2026)."),
    ("qwen", "Qwen Team. Qwen2.5 Technical Report : препринт // arXiv. – 2024. – arXiv:2412.15115."),
    ("llama", "llama.cpp : библиотека запуска языковых моделей на устройстве (лицензия MIT) [Электронный ресурс]. – "
     "URL: https://github.com/ggml-org/llama.cpp (дата обращения: 13.11.2026)."),
    ("cav", "Cavnar W. B., Trenkle J. M. N-gram-based text categorization // Proceedings of the 3rd Annual Symposium on "
     "Document Analysis and Information Retrieval (SDAIR-94). – Las Vegas, 1994. – P. 161–175."),
    ("joul", "Joulin A., Grave E., Bojanowski P., Mikolov T. Bag of tricks for efficient text classification // "
     "Proceedings of the 15th Conference of the European Chapter of the Association for Computational Linguistics "
     "(EACL). – 2017. – Vol. 2. – P. 427–431."),
    ("wein", "Weinberger K., Dasgupta A., Langford J., Smola A., Attenberg J. Feature hashing for large scale multitask "
     "learning // Proceedings of the 26th International Conference on Machine Learning (ICML). – 2009. – P. 1113–1120."),
    ("fnv", "Fowler G., Noll L. C., Vo K.-P., Eastlake D. The FNV Non-Cryptographic Hash Algorithm : Internet-Draft // "
     "IETF. – URL: https://datatracker.ietf.org/doc/draft-eastlake-fnv/ (дата обращения: 13.11.2026)."),
    ("hast", "Hastie T., Tibshirani R., Friedman J. The Elements of Statistical Learning: Data Mining, Inference, and "
     "Prediction. – 2nd ed. – New York : Springer, 2009. – 745 p."),
]
REF_NO = {k: i + 1 for i, (k, _) in enumerate(REFS)}


def cite(text):
    def rep(mm):
        keys = [k.strip().lstrip("@") for k in mm.group(1).split(",")]
        return "[" + ", ".join(str(REF_NO[k]) for k in keys) + "]"
    return re.sub(r"\[(@[a-z0-9]+(?:,\s*@[a-z0-9]+)*)\]", rep, text)


# ------------------------------------------------------------------ оформление
doc = Document()
sec = doc.sections[0]
sec.page_height, sec.page_width = Mm(297), Mm(210)
sec.left_margin, sec.right_margin = Mm(30), Mm(15)
sec.top_margin, sec.bottom_margin = Mm(20), Mm(20)
FONT = "Times New Roman"


def set_font(style, size, bold=False):
    style.font.name = FONT; style.font.size = Pt(size); style.font.bold = bold
    style.font.color.rgb = RGBColor(0, 0, 0)
    rpr = style.element.get_or_add_rPr()
    rf = rpr.find(qn("w:rFonts"))
    if rf is None:
        rf = OxmlElement("w:rFonts"); rpr.append(rf)
    for a in ("w:ascii", "w:hAnsi", "w:cs", "w:eastAsia"):
        rf.set(qn(a), FONT)


normal = doc.styles["Normal"]
set_font(normal, 14)
normal.paragraph_format.line_spacing = 1.5
normal.paragraph_format.first_line_indent = Mm(12.5)
normal.paragraph_format.space_after = Pt(0)
normal.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
for name, size in (("Heading 1", 16), ("Heading 2", 14), ("Title", 18)):
    st = doc.styles[name]; set_font(st, size, bold=True)
    st.paragraph_format.first_line_indent = Mm(0)
    st.paragraph_format.space_before = Pt(12); st.paragraph_format.space_after = Pt(6)
    st.paragraph_format.keep_with_next = True
    st.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
doc.styles["Title"].paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
lb = doc.styles["List Bullet"]; set_font(lb, 14)
lb.paragraph_format.line_spacing = 1.5; lb.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY


def p(text, align=None, indent=True, italic=False):
    par = doc.add_paragraph(); r = par.add_run(cite(text)); r.italic = italic
    if align is not None: par.alignment = align
    if not indent: par.paragraph_format.first_line_indent = Mm(0)
    return par


def bullets(items):
    for it in items:
        par = doc.add_paragraph(style="List Bullet")
        if isinstance(it, tuple):
            r = par.add_run(cite(it[0])); r.bold = True; par.add_run(cite(it[1]))
        else:
            par.add_run(cite(it))


def formula(text):
    par = doc.add_paragraph(); par.paragraph_format.first_line_indent = Mm(0)
    par.alignment = WD_ALIGN_PARAGRAPH.CENTER; r = par.add_run(text); r.italic = True


def h1(t): doc.add_heading(t, level=1)
def h2(t): doc.add_heading(t, level=2)


def shade(cell, color):
    tcPr = cell._tc.get_or_add_tcPr(); shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear"); shd.set(qn("w:color"), "auto"); shd.set(qn("w:fill"), color); tcPr.append(shd)


TNO = [0]


def table(caption, header, rows, widths):
    TNO[0] += 1
    cap = doc.add_paragraph(); cap.paragraph_format.first_line_indent = Mm(0)
    cap.paragraph_format.keep_with_next = True; cap.paragraph_format.space_before = Pt(6)
    cap.add_run(f"Таблица {TNO[0]} – {caption}")
    t = doc.add_table(rows=1, cols=len(header)); t.style = "Table Grid"; t.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, h in enumerate(header):
        c = t.rows[0].cells[i]; c.text = ""; rr = c.paragraphs[0].add_run(h); rr.bold = True; shade(c, "E7E6E6")
    for row in rows:
        cells = t.add_row().cells
        for i, v in enumerate(row): cells[i].text = cite(str(v))
    for row in t.rows:
        for i, c in enumerate(row.cells):
            c.width = Mm(widths[i])
            for par in c.paragraphs:
                par.paragraph_format.first_line_indent = Mm(0); par.paragraph_format.line_spacing = 1.0
                par.alignment = WD_ALIGN_PARAGRAPH.LEFT
                for r in par.runs: r.font.size = Pt(12)
    doc.add_paragraph().paragraph_format.first_line_indent = Mm(0)


# ------------------------------------------------------------------ числа из расчётов
ALL = A["все"]; GE6 = A["от 6 прошлых стартов"]; LT6 = A["меньше 6 прошлых стартов"]
N_TF = A["TFRRS (NCAA)"]["athletes"]; N_ROWS_TF = A["TFRRS (NCAA)"]["n"]
SES_GAIN = 1 - ALL["ses"] / ALL["naive"]
GCL = GC["links"]; GCF = GC["forecast"]

# ================================================================== титул
doc.add_paragraph(style="Title").add_run("Исследования в области искусственного интеллекта и анализа данных в проекте «ДневникТройного»")
p("Мобильное приложение для спортсмена-прыгуна тройным и тренера (Android, Kotlin, Jetpack Compose, Room)",
  align=WD_ALIGN_PARAGRAPH.CENTER, indent=False)
p(f"Документ ведётся по мере развития проекта. Последнее обновление: {UPDATED}.",
  align=WD_ALIGN_PARAGRAPH.CENTER, indent=False, italic=True)

h1("Содержание")
TOC = ["1 Цель и рамки исследований", "2 Выбор подхода", "3 Данные", "4 Прогноз результата",
       "5 Признаки из тренерской литературы", "6 Поиск связей упражнений с результатом",
       "7 Связь «тренировка → результат» на смежном виде спорта", "8 Справочные связи по исследованиям",
       "9 Резкий спад результата", "10 Нагрузка по времени тренировок", "11 Правила советов",
       "12 Демо-данные для проверки моделей", "13 Автоматические тесты", "14 Свой помощник «Спросить дневник»",
       "15 Идеи развития ИИ", "16 Правовые аспекты", "17 Ограничения", "Список литературы",
       "Приложение А. Журнал изменений"]
for line in TOC: p(line, indent=False)

# ================================================================== 1
h1("1 Цель и рамки исследований")
p("Цель — дать спортсмену и тренеру выводы по данным дневника, которым можно доверять: прогноз результата в тройном "
  "прыжке к старту или к сроку цели, найденные в данных спортсмена связи упражнений с результатом, предупреждения "
  "о резком спаде и о росте нагрузки, а также советы по подготовке.")
p("Рамки, заданные проектом:")
bullets([
    ("Работа на телефоне без сети. ", "Все расчёты выполняются локально; данные спортсмена никуда не передаются. "
     "Модели обучаются заранее на открытых данных, в приложение переносятся только подобранные параметры."),
    ("Объяснимость. ", "У каждого вывода видно, на каких данных он сделан и насколько он надёжен; формулировки "
     "рассчитаны на спортсмена, а не на статистика (без слов «тренд», «доверительный коридор»)."),
    ("Мало данных у одного человека. ", "За сезон у спортсмена набираются десятки точек, поэтому модели должны "
     "работать на малых выборках и честно сообщать, когда данных не хватает (холодный старт)."),
    ("Проверка на реальных данных. ", "Каждая модель проверяется на реальных результатах спортсменов, "
     "а не только на сгенерированных данных (раздел 3.3)."),
])
p("Реализация: data/Insights.kt (прогноз, связи, резкий спад, нагрузка), data/Advice.kt (правила советов), экран "
  "«Советы и прогноз» (AdviceScreen.kt), раздел «Как считаются советы» в приложении. Скрипты исследования (сбор "
  "открытых данных, проверка моделей) написаны на Python с библиотекой scikit-learn [@skl].")

# ================================================================== 2
h1("2 Выбор подхода")
p("Рассматривались три уровня «интеллектуальности» аналитики:")
table("Варианты подхода к аналитике",
      ["Уровень", "Что это", "Решение"],
      [["1. Статистические модели на устройстве",
        "Сглаживание, корреляция со сдвигом, перестановочные тесты, робастные оценки, правила",
        "Реализован: работает на малых данных, объясним, не требует сети"],
       ["2. Модели, обученные на данных многих спортсменов",
        "Параметры подбираются на открытых данных (150 прыгунов тройным, 13 спортсменов мирового уровня, велосипедисты "
        "GoldenCheetah) и переносятся в приложение",
        "Реализован частично: вес сглаживания в прогнозе и кривая прироста по возрасту обучены на реальных данных; "
        "сравнивались линейная регрессия Хьюбера и градиентный бустинг (раздел 4.2)"],
       ["3. Языковые модели и компьютерное зрение",
        "Разбор заметок, анализ видео прыжка",
        "Реализованы свои модели понимания вопросов и заметок (раздел 14) — без чужой языковой модели; генеративная "
        "модель и видео — развитие (раздел 15)"]],
      [45, 60, 60])
p("Учтено, что связь, найденная при сравнении разных спортсменов (кто сильнее в тесте, тот дальше прыгает), не "
  "означает, что у одного спортсмена показатели будут меняться вместе от недели к неделе [@mol, @fish]. Поэтому "
  "модель связей ищет совпадения в данных одного спортсмена, а сведения из литературы показываются отдельно (раздел 8).")

# ================================================================== 3
h1("3 Данные")
h2("3.1 Данные дневника")
p("В дневнике хранятся тренировки (дата, время начала и окончания, пропуск и причина, заметки), упражнения и подходы "
  "с результатами (метры, сантиметры, секунды, килограммы, повторения; одна или две ноги; инвентарь), соревнования "
  "с попытками и заступами, цели со сроками, вес тела и профиль (дата рождения, личный рекорд). Для моделей результаты "
  "собираются в ряды: лучший результат дня (прогноз) и лучший результат недели (связи).")
h2("3.2 Демо-дневник")
p("Для проверки моделей в приложении есть демо-дневник за год с заранее заложенными закономерностями (раздел 12). "
  "Он открывается и сбрасывается к исходному виду из настроек.")
h2("3.3 Реальные данные")
p("Открытого набора, где были бы и журнал тренировочных упражнений, и результаты в тройном одного спортсмена по "
  "неделям, найти не удалось (поиск по Zenodo, figshare, Kaggle, PubMed, OSF). Поэтому собраны три вида открытых данных:")
table("Открытые данные, на которых проверялись модели",
      ["Источник", "Что взято", "Объём", "Для чего"],
      [["TFRRS — протоколы студенческих соревнований США [@tfrrs]",
        "Все засчитанные старты в тройном, ветер, название старта; у тех же спортсменов — длина, высота, шест",
        f"{N_TF} спортсменов, {N_ROWS_TF} прогнозов следующего старта",
        "Подбор и проверка прогноза; признаки из литературы"],
       ["TFRRS, отдельные спортсмены (псевдонимы A, B, C; D, E)",
        "Тройной; длина и тройной на одних соревнованиях", "5 спортсменов",
        "Независимая проверка прогноза; проверка связей (тесты в приложении)"],
       ["World Athletics — профили спортсменов из мировых топ-листов [@wa]",
        "Дата рождения, лучший результат каждого сезона, старты текущего сезона",
        f"{B['athletes']} спортсменов, {B['все']['n']} пар «сезон → следующий сезон»",
        "Прирост по возрасту (холодный старт, юниоры)"],
       ["GoldenCheetah OpenData — журналы тренировок велосипедистов [@gc]",
        "По каждой тренировке: дата, длительность, нагрузка TSS, максимальная мощность на отрезках 10 с … 20 мин",
        f"{GC['athletes']} спортсменов с достаточной историей, {GCF['weeks']} недель",
        "Связь «тренировка → результат» на реальных журналах тренировок (раздел 7)"]],
      [45, 50, 35, 35])
p("Сбор шёл с паузами между запросами; данные обезличены в расчётах (номер вместо имени), в приложение попадают только "
  "подобранные параметры моделей и пять рядов TFRRS для тестов. Сайт World Athletics после 13 профилей перестал "
  "отдавать страницы; обходить ограничение не стали, поэтому выборка по юниорам мала.")

# ================================================================== 4
h1("4 Прогноз результата")
h2("4.1 Модель")
p("Прогноз строится к ближайшему старту из календаря (если его нет — на 8 недель вперёд), к сроку каждой цели и по "
  "любому показателю, который выберет пользователь (к старту или через 4, 8, 12 недель). Ряд — лучший результат "
  "каждого дня, не больше 30 последних; после перерыва дольше 400 дней ряд начинается заново. Для тройного берутся "
  "главный рекорд (полный разбег) и результаты стартов.")
p("Если есть хотя бы 3 дня с результатами, прогноз — сглаженный уровень (простое экспоненциальное сглаживание "
  "[@gard, @hyn]):")
formula("L₁ = y₁;   Lₖ = w · yₖ + (1 − w) · Lₖ₋₁;   прогноз = Lₙ,")
p("где yₖ — результат k-го дня, w = 0,3, а для первого результата после перерыва дольше 45 дней (межсезонье) w = 0,6. "
  "Свежие результаты весят больше, а один неудачный или случайно удачный старт не сбивает прогноз.")
p("Надёжность показывается словами и зависит от числа результатов, их ровности (медиана отклонений от уровня в "
  "сравнении с естественным разбросом 2,5 %, раздел 5) и того, насколько далеко дата:")
bullets([("высокая — ", "от 10 дней с результатами, отклонения не больше 2,5 %, дата не дальше 8 недель;"),
         ("средняя — ", "от 5 дней, отклонения не больше 5 %, дата не дальше 16 недель;"),
         ("низкая — ", "остальные случаи и любой холодный старт.")])
h2("4.2 Как выбиралась модель")
p(f"Модели сравнивались скользящей проверкой [@tash]: для каждого старта прогноз строится только по более ранним "
  f"стартам. Обучаемые модели оценивались перекрёстной проверкой по спортсменам (5 частей; спортсмен целиком либо в "
  f"обучении, либо в проверке), чтобы модель не «узнавала» спортсмена. Данные — {N_TF} прыгунов тройным TFRRS и "
  f"{A['World Athletics, сезон 2026']['athletes']} спортсменов World Athletics, всего {ALL['n']} прогнозов.")
rows = [["«Как на прошлом старте» (эталон)", m(ALL["naive"]), m(GE6["naive"]), m(LT6["naive"])],
        ["Прежняя модель приложения: среднее последнего результата и прямой линии", m(ALL["app"]), m(GE6["app"]), m(LT6["app"])],
        ["Среднее трёх последних стартов", m(ALL["mean3"]), m(GE6["mean3"]), m(LT6["mean3"])],
        ["Регрессия Хьюбера на 8 признаках [@hub]", m(ALL["huber"]), m(GE6["huber"]), m(LT6["huber"])],
        ["Гребневая регрессия на тех же признаках", m(ALL["ridge"]), m(GE6["ridge"]), m(LT6["ridge"])],
        ["Градиентный бустинг деревьев", m(ALL["gbm"]), m(GE6["gbm"]), m(LT6["gbm"])],
        ["Сглаживание с обученным весом (текущая модель)", m(ALL["ses"]), m(GE6["ses"]), m(LT6["ses"])]]
table("Средняя абсолютная ошибка прогноза следующего старта, м",
      ["Модель", "Все прогнозы", "От 6 прошлых стартов", "Меньше 6"], rows, [75, 30, 30, 30])
lo, hi = ALL["huber_vs_app_ci"]
p(f"Прежняя модель приложения почти не лучше «как в прошлый раз» ({m(ALL['app'])} против {m(ALL['naive'])} м): "
  f"прямая линия на шумных рядах больше мешает, чем помогает. Обучаемые модели точнее на {pc(SES_GAIN)}; разница "
  f"с прежней моделью статистически надёжна (95 % интервал бутстрепа по спортсменам: от {m(-hi)} до {m(-lo)} м в пользу "
  f"новой). Градиентный бустинг переобучается и проигрывает даже эталону. Регрессия Хьюбера и сглаживание дают "
  f"одинаковую точность; выбрано сглаживание — у него один параметр и простое объяснение для спортсмена.")
p("Вес сглаживания подбирался на одной половине спортсменов и проверялся на другой (и наоборот): лучший вес 0,3–0,4 "
  "в обоих случаях. Затухающий тренд Холта (β = 0…0,2) [@hyn] на этих данных ни разу не оказался лучше: результат "
  "одного старта колеблется сильнее, чем растёт за сезон. Повышенный вес первого старта после межсезонья (0,6 после "
  "перерыва дольше 45 дней) дал небольшой, но устойчивый выигрыш на обеих половинах (0,378 против 0,381 м).")
h2("4.3 Независимая проверка на трёх спортсменах")
p("Три ряда TFRRS, не входившие в подбор, записаны в автоматический тест приложения (RealDataBacktestTest):")
table("Ошибка прогноза следующего старта на независимых рядах, м",
      ["Спортсмен", "Текущая модель", "«Как на прошлом старте»", "Прежняя модель"],
      [["Спортсмен A (45 стартов)", "0,250", "0,236", "0,228"], ["Спортсмен B (51)", "0,351", "0,396", "0,364"],
       ["Спортсмен C (41)", "0,465", "0,492", "0,488"], ["Среднее", "0,355", "0,375", "0,360"]],
      [55, 35, 40, 35])
p("На этих трёх рядах прежняя модель была подобрана (окно 180 дней выбиралось именно по ним), поэтому её цифры здесь "
  "завышены; на 150 новых спортсменах она почти не лучше эталона. Текущая модель в среднем лучше эталона, но у "
  "спортсмена A — хуже: результат резко меняется от сезона к сезону. Это оставлено в тесте как честная оговорка.")
h2("4.4 Холодный старт и возраст")
p("Пока результатов меньше трёх, прогноз берёт последний результат (или личный рекорд из профиля) и добавляет "
  "типичный годовой прирост по возрасту: до 18 лет — 4 %, 18–23 года — 1,5 %, с 24 лет — 0 % (возраст неизвестен — "
  "1,5 %). Цифры выбраны по литературе о юных прыгунах тройным [@ecss] и пиковом возрасте прыгунов [@bocc] и "
  "проверены на профилях World Athletics:")
Bn = {k: v for k, v in B.items() if isinstance(v, dict) and "naive" in v}
table("Ошибка прогноза лучшего результата следующего сезона, м",
      ["Возраст", "Пар сезонов", "«Как в прошлом сезоне»", "Прирост по возрасту (приложение)", "Обученная кривая"],
      [[k, v["n"], m(v["naive"]), m(v["rule"]), m(v["learned"])] for k, v in Bn.items()],
      [35, 25, 35, 40, 30])
cur = B["curve"]
p(f"Кривая прироста, обученная на этих данных (сглаживание по соседним возрастам), близка к литературной: "
  f"13–15 лет — около {pc(cur['14'], 0)} в год, 17 лет — {pc(cur['17'], 1)}, 19–21 год — {pc(cur['20'], 1)}, "
  f"24–26 лет — {pc(cur['25'], 1)}, после 27 лет — около нуля. Правило приложения даёт ту же точность, что и обученная "
  f"кривая, поэтому оставлено. Ограничение: всего {B['athletes']} спортсменов мирового уровня (они «дожили» до топ-листов, "
  f"поэтому прирост юниоров может быть завышен).")
h2("4.5 Что пробовали и отказались")
bullets([
    ("Окно 9 месяцев и 1 год, затем 180 дней до даты прогноза с линией. ", "Подобраны по трём спортсменам; на 150 "
     "новых почти не лучше «как в прошлый раз». Заменено сглаживанием, обученным на многих спортсменах."),
    ("Тренд (линия, затухающий тренд Холта). ", "На реальных стартах ухудшал прогноз."),
    ("Градиентный бустинг. ", "Переобучается на малых рядах; к тому же его трудно объяснить и перенести в приложение."),
    ("«Тренд» и «коридор» на экране. ", "Непонятны спортсменам; оставлены ожидаемое значение, надёжность словами, "
     "успеваешь ли к цели и фраза о том, на чём основан прогноз."),
    ("Смешивание разных вариантов тройного. ", "Прогноз мешал «тройной с 6 шагов» с полным разбегом; теперь "
     "берётся главный рекорд."),
])

# ================================================================== 5
h1("5 Признаки из тренерской литературы")
p("Из литературы по лёгкой атлетике взяты факторы, которые могут влиять на результат конкретного старта, и проверены "
  "на тех же данных как дополнительные признаки регрессии Хьюбера:")
champ = A["champ_bias"]
table("Факторы из литературы и их проверка",
      ["Фактор", "Что известно", "Что показали данные"],
      [["Естественный разброс результата",
        "У прыгунов тройным мирового уровня результат от старта к старту колеблется примерно на 1,7 %; значимое "
        "изменение — около 0,3 этого разброса [@hop]",
        f"У спортсменов NCAA разброс внутри сезона {pc(A['empirical_cv'], 1)} (у спортсменов ниже уровнем он больше, "
        f"как и у Hopkins). Используется как порог надёжности прогноза"],
       ["Ветер",
        "Попутный ветер 2 м/с даёт в тройном в среднем +0,10 м (≈ 5 см на 1 м/с) [@wind]",
        "Поправка истории на ветер почти не меняет точность прогноза следующего старта"],
       ["Главный старт сезона и подводка",
        "Правильная подводка (снижение объёма на 41–60 % за 2 недели) улучшает результат в среднем на 2–3 % [@bosq, @muj]",
        f"На чемпионатах результат выше прошлого в среднем на {m(champ['mean_y_minus_last'], 2)} м против "
        f"{m(champ['mean_y_minus_last_other'], 2)} м на обычных стартах; коэффициент признака +{m(A['lit_coef']['tgt_champ'], 2)} м"],
       ["Зал и стадион, фаза сезона", "Сезон зала и стадиона, рост к концу сезона [@spy]",
        "Влияние на прогноз в пределах погрешности"]],
      [35, 65, 65])
lo, hi = ALL["lit_vs_huber_ci"]
p(f"Вместе литературные признаки улучшили прогноз с {m(ALL['huber'])} до {m(ALL['huber_lit'])} м (95 % интервал "
  f"разности от {m(lo)} до {m(hi)} м) — в пределах погрешности. Отдельно заметен только эффект главного старта: на "
  f"чемпионатах прыгают дальше ожидаемого, что согласуется с литературой о подводке. В дневнике пока нет отметки "
  f"«главный старт», поэтому это предложено как развитие (раздел 15), а порог разброса используется уже сейчас.")

# ================================================================== 6
h1("6 Поиск связей упражнений с результатом")
h2("6.1 Модель")
p("Задача — найти упражнения, колебания которых у данного спортсмена совпадают с колебаниями результата в тройном "
  "в ту же неделю или через 1–6 недель.")
bullets([
    "Ряды: лучший результат недели в упражнении и в тройном; для показателей «меньше — лучше» (бег) знак меняется.",
    "Удаление тренда: из обоих рядов вычитается линейный тренд по номеру недели — иначе два ряда, просто растущих за "
    "сезон, дают ложную «связь» [@gran, @box].",
    "Для каждого сдвига 0…6 недель считается коэффициент корреляции Пирсона по общим неделям (нужно не меньше 8); "
    "выбирается сдвиг с наибольшим |r|; меньше 0,3 — связь не рассматривается.",
    "Перестановочный тест [@phip, @good]: недели упражнения 499 раз перемешиваются, и каждый раз заново выбирается "
    "лучший сдвиг — так учитывается, что лучший сдвиг выбирался из семи.",
    "Поправка Бенджамини–Хохберга [@bh] на число проверенных упражнений с долей ложных находок 5 %.",
    "Показываются до 5 связей обычными словами: «связь сильная / заметная / слабая», «в ту же неделю / через N нед.», "
    "как часто такое бывает случайно.",
])
h2("6.2 Проверка на шуме и на прыгунах")
table("Проверка модели связей",
      ["Проверка", "Прежнее правило", "Текущая модель"],
      [["Чистый шум, доля ложных связей", "42 %", "≈ 6 %"],
       ["Спортсмен D: длина → тройной (TFRRS)", "—", "p = 0,35, связи нет"],
       ["Спортсмен E: длина → тройной (TFRRS)", "—", "p = 0,93, связи нет"],
       ["Демо: присед → тройной через 3 нед.", "—", "находится"],
       ["Велоспорт, связанные показатели (раздел 7)", "—",
        f"находится у {pc(GCL['мощность 20 мин']['share_found'])} спортсменов, ложных — {pc(GCL['мощность 20 мин']['share_control'])}"]],
      [70, 35, 60])
p("Прежнее правило (|r| ≥ 0,5 и t ≥ 2 по лучшему из сдвигов) находило ложную связь на чистом шуме в 42 % случаев — "
  "перебор сдвигов без поправки многократно повышает шанс случайного совпадения. У прыгунов TFRRS длина и тройной от "
  "недели к неделе почти не совпадают (r от −0,39 до 0,4), хотя между спортсменами эти виды связаны сильно [@mol, @fish].")
p("Сама по себе проверка «на прыгунах связи нет» не доказывает, что модель умеет находить настоящие связи. Такую "
  "проверку дал смежный вид спорта с реальными журналами тренировок (раздел 7).")

# ================================================================== 7
h1("7 Связь «тренировка → результат» на смежном виде спорта")
h2("7.1 Данные и как они приведены к дневнику")
p(f"Набор GoldenCheetah OpenData [@gc] — обезличенные журналы тренировок тысяч спортсменов (в основном "
  f"велосипедистов) с мощностью по каждой тренировке. Скачаны журналы 60 спортсменов; в анализ вошли {GC['athletes']}, "
  f"у которых не меньше 30 недель с измеренной мощностью. Данные приведены к виду дневника тройного:")
table("Соответствие велосипедных данных дневнику тройного",
      ["В дневнике тройного", "В велосипедных данных"],
      [["Результат: лучший тройной недели", "Лучшая мощность на 5 минутах за неделю"],
       ["Скоростное упражнение (бег 30 м)", "Лучшая мощность за 10 секунд"],
       ["Скоростно-силовое и силовое упражнение", "Лучшая мощность за 1 минуту"],
       ["Общая выносливость", "Лучшая мощность за 20 минут"],
       ["Время тренировок (начало и конец)", "Часы за неделю"],
       ["Нагрузка", "Нагрузка TSS за неделю (мощность × время)"]],
      [80, 85])
h2("7.2 Модель связей приложения на настоящих журналах")
p("Модель связей запускалась без изменений на каждом спортсмене. Контроль: те же ряды, сдвинутые по времени на "
  "случайные 20–60 недель, — настоящая связь при этом разрушается, и находки — ложные.")
link_rows = []
for name, v in GCL.items():
    lags = v["lag_hist"]; same = lags.get("0", 0); total = sum(lags.values())
    link_rows.append([name, v["athletes_tested"], pc(v["share_found"]), pc(v["share_control"]),
                      f"{same} из {total}" if total else "—",
                      m(v["median_abs_r"], 2) if v["median_abs_r"] is not None else "—"])
table("Найденные связи с результатом (мощность 5 мин) у велосипедистов",
      ["Показатель", "Спортсменов", "Связь найдена", "Ложные (контроль)", "Из них в ту же неделю", "Типичный |r|"],
      link_rows, [45, 22, 22, 25, 28, 22])
p("Выводы:")
bullets([
    "Модель находит настоящие связи на реальных тренировочных данных: связанные физиологически показатели совпадают "
    "с результатом у большинства спортсменов, а на контрольных рядах ложных находок мало.",
    "Почти все найденные связи — в ту же неделю. Отложенного на 1–6 недель влияния модель на реальных данных почти не "
    "видит: понедельные колебания отражают текущее состояние, а не эффект тренировки через месяц. Для дневника "
    "тройного это значит, что связи «через N недель» надо показывать особенно осторожно.",
    "Объём и нагрузка совпадают с результатом реже и тоже в ту же неделю: в недели, когда много и тяжело тренируются, "
    "чаще и выкладываются на полную. Это совпадение, а не доказательство пользы объёма.",
])
h2("7.3 Модель «форма — усталость» (Банистер)")
p("В тренерской литературе связь нагрузки и результата описывают моделью «форма — усталость» [@ban]: результат = "
  "базовый уровень + «форма» (нагрузка, сглаженная за ~42 дня) − «усталость» (нагрузка, сглаженная за ~7 дней). "
  "Проверено, улучшают ли эти признаки прогноз результата следующей недели, если к ним добавить историю результатов "
  "(регрессия Хьюбера, перекрёстная проверка по спортсменам).")
table("Ошибка прогноза мощности 5 мин на следующей неделе, % от результата",
      ["Модель", "Ошибка"],
      [["«Как на прошлой неделе»", m(GCF["naive_%"], 2)],
       ["Среднее трёх последних недель", m(GCF["mean3_%"], 2)],
       ["Обучаемая модель без нагрузки", m(GCF["learned_no_load_%"], 2)],
       ["+ «форма — усталость» по нагрузке TSS", m(GCF["learned_plus_tss_%"], 2)],
       ["+ «форма — усталость» по часам тренировок (как в дневнике)", m(GCF["learned_plus_hours_%"], 2)]],
      [110, 40])
lo, hi = GCF["tss_vs_base_ci"]
p(f"Добавление нагрузки не улучшает прогноз (разность в процентных пунктах, 95 % интервал: от {m(lo, 2)} до "
  f"{m(hi, 2)}). Это согласуется с исследованиями: параметры модели Банистера неустойчивы и плохо обобщаются [@hel], "
  f"а модели, обученные на группе спортсменов, обобщаются лучше индивидуальных [@imb]. Поэтому в приложении нагрузка "
  f"используется для предупреждений (раздел 10), а не для прогноза результата.")

# ================================================================== 8
h1("8 Справочные связи по исследованиям")
p("Блок «По исследованиям» на экране показывает, что по научным данным связано с результатом в тройном, и отмечает, "
  "записывает ли спортсмен такие упражнения. Это сравнения разных спортсменов — подсказка, что стоит записывать.")
table("Связи с результатом в тройном по литературе",
      ["Показатель", "Что известно", "Источник"],
      [["Специальный пятерной прыжок с укороченного разбега",
        "Регрессия по результатам теста объяснила 96 % различий в соревновательном тройном (R² = 0,96)", "[@mazz]"],
       ["Скорость бега 60 и 100 м",
        "Очень сильная связь с тройным с места у спортсменов NCAA (r около −0,97…−1,0; выборка мала)", "[@hud, @lin]"],
       ["Горизонтальные прыжки и разгон / максимальная скорость",
        "Мета-анализ: многоскоки и прыжки в длину с места заметно связаны со скоростью бега", "[@lin]"],
       ["Присед на 1 кг веса тела",
        "Относительная сила связана с прыжком сильнее, чем абсолютная; со временем бега 5–30 м r = −0,32…−0,42", "[@wag]"]],
      [45, 95, 25])

# ================================================================== 9
h1("9 Резкий спад результата")
p("Последний результат в упражнении (не старше 14 дней) сравнивается с нормой спортсмена — медианой до 20 предыдущих "
  "результатов (нужно не меньше 8). Разброс оценивается медианой абсолютных отклонений (MAD) и считается "
  "модифицированная z-оценка [@igl]:")
formula("z = 0,6745 · (x − медиана) / MAD.")
p("Спадом считается |z| ≥ 3 в худшую сторону (порог из [@igl] — 3,5; взят чуть строже, так как совет лишь предлагает "
  "проверить самочувствие). Показываются не больше двух самых сильных спадов.")

# ================================================================== 10
h1("10 Нагрузка по времени тренировок")
p("По времени начала и окончания тренировок считается отношение острой нагрузки к хронической (ACWR) [@gab, @hul]: "
  "сумма минут за 7 дней делится на среднее в неделю за 28 дней. При отношении от 1,5 и не меньше 4 тренировок за "
  "месяц выдаётся предупреждение. ACWR критикуется за математические недостатки [@imp], а проверка на велосипедистах "
  "(раздел 7.3) показала, что нагрузка не помогает прогнозировать результат, — поэтому это подсказка добавить "
  "восстановление, а не «прогноз травмы» или результата.")

# ================================================================== 11
h1("11 Правила советов")
p("Помимо моделей, советы формируются прозрачными правилами (data/Advice.kt); у каждого совета есть пояснение "
  "«почему», и его можно скрыть.")
table("Правила советов",
      ["Правило", "Условие"],
      [["Разбег", "Последние 10 попыток за 30 дней (от 4): доля заступов от 50 % или стабильность ниже 70 %"],
       ["Цели", "Нужный темп в неделю против фактического за 8 недель: отставание в 2 раза и больше"],
       ["Застой", "Рекорд в тройном держится 6+ недель при 3+ прыжках"],
       ["Нагрузка", "Повторения за неделю выросли на 40 % (и минимум на 20) к среднему трёх прошлых недель"],
       ["Подводка к старту", "За 7 дней до старта: снижен ли объём, отдых в последние два дня [@bosq, @muj]"],
       ["Пропуски", "3+ пропуска за 30 дней с самой частой причиной"],
       ["Заметки", "Слова «болит», «тянет», «травма» и т. п. в 2 из 3 последних тренировок"],
       ["Вес", "Изменение на 3 % и больше за месяц"],
       ["Рекорды", "Рекорд, обновлённый за последние 7 дней"],
       ["Резкий спад, нагрузка по времени", "Модели из разделов 9 и 10"]],
      [45, 120])

# ================================================================== 12
h1("12 Демо-данные для проверки моделей")
p("Демо-дневник за год (data/DemoSignals.kt, data/SeedDatabase.kt) построен так, чтобы каждая модель находила "
  "заложенное и не находила лишнего. Значения детерминированы (фиксированное зерно генератора).")
bullets([
    "Тройной растёт за сезон примерно на 0,85 м.",
    "Колебания приседа (волна около 5 недель) повторяются в тройном через 3 недели — сильная связь.",
    "Колебания бега 30 м (волна около 7 недель) повторяются в тройном через 2 недели — связь слабее.",
    "Остальные упражнения — тренд и независимый шум: связей по ним быть не должно.",
    "Заложены также 7 стартов, цели, заступы, жалобы в заметках, пропуски, спад в беге 60 м и всплеск времени тренировок.",
])

# ================================================================== 13
h1("13 Автоматические тесты")
table("Тесты аналитических моделей",
      ["Тест", "Что проверяет"],
      [["InsightsTest", "Сглаженный уровень, вес после межсезонья, устойчивость к одному странному результату, холодный "
        "старт, рекорд из профиля, «меньше — лучше», прирост по возрасту, цели, связи, резкий спад, нагрузка"],
       ["RealDataBacktestTest", "Прогноз на трёх независимых рядах TFRRS: точные ошибки и в среднем лучше «как на прошлом старте»"],
       ["LinksRealDataTest", "Нет ложной связи у спортсменов D и E; на шуме ложных связей не больше 12 % из 60 запусков"],
       ["DemoSignalsTest", "Демо-сигналы: заложенные связи находятся, лишние — нет"],
       ["TrainingTimeTest", "Длительность и подпись времени тренировки"],
       ["MigrationTest.migrate24To25", "Миграция базы стирает заглушку 60 минут из старых версий"],
       ["AssistantModelTest", "Kotlin считает признаки и вероятности своих текстовых моделей так же, как Python при "
        "обучении (контрольные пары); разбор заметок; совет о жалобах по модели; распознавание вопросов и ответы помощника"]],
      [55, 110])
p("Скрипты исследования (сбор данных TFRRS, World Athletics и GoldenCheetah, сравнение моделей, проверка модели связей "
  "на велосипедистах) хранятся отдельно от приложения; результаты в этом документе получены ими.")

# ================================================================== 14
AI_ = AS["intents"]; AN = AS["notes"]
h1("14 Свой помощник «Спросить дневник»")
p("Вместо подключения чужой языковой модели (лицензии, размер 1,5 ГБ, риск выдуманных чисел) сделаны свои небольшие "
  "модели машинного обучения для двух задач: понять, о чём спрашивает спортсмен, и понять, что написано в заметке к "
  "тренировке. Ответ собирается из данных дневника и уже проверенных моделей (прогноз, связи, советы, рекорды), "
  "поэтому числа в ответах не выдумываются.")
h2("14.1 Устройство")
bullets([
    ("Признаки текста. ", "Текст приводится к нижнему регистру, «ё» → «е», знаки препинания убираются. Признаки — "
     "слова, буквенные n-граммы длиной 2–4 каждого слова и пары соседних слов [@cav, @joul]; они делают модель "
     "устойчивой к опечаткам и окончаниям («присед», «присяд», «приседаний»). Каждый признак переводится в номер "
     "хеш-функцией FNV-1a [@fnv] по модулю 4096 («hashing trick» [@wein]) — словарь хранить не нужно."),
    ("Понимание вопроса. ", f"Многоклассовая логистическая регрессия [@hast] на {len(AI_['classes'])} типов: прогноз, "
     "итоги периода, упражнение, рекорды, что влияет на результат, готовность к старту, нагрузка, самочувствие, "
     "ближайшие старты, цели, советы, разбег, помощь и «не о дневнике». Если уверенность ниже порога "
     f"{m(AI_['threshold'], 2)}, помощник честно отвечает «не уверен, что понял» и показывает примеры."),
    ("Упражнение в вопросе. ", "Слова вопроса сравниваются с названиями упражнений из дневника по первым пяти буквам "
     "и числам («бег 30»)."),
    ("Разбор заметок. ", "По отдельной логистической регрессии на каждую метку: боль, усталость, плохой сон, хорошее "
     "самочувствие, техника, болезнь (у заметки может быть несколько меток или ни одной)."),
    ("Перенос в приложение. ", f"Модели обучаются в Python (scikit-learn [@skl]), веса выгружаются текстом в ресурсы "
     f"приложения ({m(AS['model_size_kb']['intent_model.txt'], 0)} и {m(AS['model_size_kb']['notes_model.txt'], 0)} КБ). "
     "В Kotlin признаки и вероятности считаются по тем же формулам; совпадение с Python до 10⁻⁵ проверяет тест."),
])
h2("14.2 Данные")
p(f"Обучающие примеры написаны для проекта (собственные формулировки, без чужих текстов): {AI_['base_phrases']} "
  f"вопросов, включая шаблоны вида «как мой {{упражнение}}» с названиями упражнений; к каждому добавлены вариант с "
  f"опечаткой и вариант с другой пунктуацией — всего {AI_['train_examples']} примеров. Заметок — {AN['base_notes']}, "
  f"с вариантами — {AN['train_examples']}. Для проверки отдельно написаны «трудные» наборы, не участвующие в обучении: "
  f"{AI_['hard_n']} вопросов разговорным языком, с опечатками и сокращениями («скока я прыгну на старте», «че по "
  f"прогнозу») и {AN['hard_n']} заметок.")
h2("14.3 Проверка")
table("Понимание вопросов: доля верно определённых типов",
      ["Способ", "Перекрёстная проверка", "Трудный набор"],
      [["Ключевые слова (базовая линия)", pc(AI_["keywords_train_accuracy"]), pc(AI_["keywords_hard_accuracy"])],
       ["Своя модель", pc(AI_["cv_accuracy"]), pc(AI_["hard_accuracy"])]],
      [70, 45, 45])
p(f"Перекрёстная проверка сгруппирована по исходной фразе: формулировки из проверочной части модель не видела "
  f"вовсе, поэтому оценка строгая ({pc(AI_['cv_accuracy'])}; средний F1 по типам {m(AI_['cv_macro_f1'], 2)}). На "
  f"трудном наборе модель, обученная на всех примерах, верно поняла {pc(AI_['hard_accuracy'])} вопросов против "
  f"{pc(AI_['keywords_hard_accuracy'])} у ключевых слов.")
cvf = AN["cv_f1"]; hf = AN["hard_f1"]
table("Разбор заметок: F1 по меткам",
      ["Метка", "Перекрёстная проверка", "Трудный набор"],
      [[lab, m(cvf[k], 2), m(hf[k], 2)] for k, lab in (("pain", "боль"), ("fatigue", "усталость"), ("sleep", "плохой сон"),
                                                        ("good", "хорошее самочувствие"), ("technique", "техника"),
                                                        ("illness", "болезнь"))],
      [60, 50, 50])
p(f"Для боли модель сравнивалась с прежним правилом по ключевым словам («болит», «тянет», «травма»…): F1 на трудном "
  f"наборе {m(AN['pain_model_hard_f1'], 2)} против {m(AN['pain_regex_hard_f1'], 2)} — модель понимает «колено чет "
  f"побаливает», «ахил ноет», «неприятно в стопе». Поэтому совет «Жалобы в заметках» теперь срабатывает по ключевым "
  f"словам или по модели. Ограничение: обучающих заметок пока немного, и метки «болезнь» и «хорошее самочувствие» "
  f"на перекрёстной проверке распознаются заметно хуже ({m(cvf['illness'], 2)} и {m(cvf['good'], 2)}); точность "
  f"вырастет, если добавить реальные заметки спортсменов и тренера (с их согласия).")
h2("14.4 В приложении")
p("Раздел «Советы и прогноз» → «Спросить дневник»: поле вопроса, готовые вопросы, ответ с пометкой, как понят "
  "вопрос и с какой уверенностью, и кнопкой перехода в нужный раздел. Всё работает на телефоне без сети; переписка "
  "не сохраняется, так как может касаться здоровья (раздел 16).")

# ================================================================== 15
h1("15 Идеи развития ИИ")
table("Предлагаемые направления",
      ["Идея", "Что даст", "Что нужно"],
      [["Отметка «главный старт» и поправка на подводку",
        "Данные показали: на чемпионатах прыгают дальше ожидаемого; литература — подводка даёт 2–3 % [@bosq, @muj]",
        "Поле у соревнования; коэффициент обучить на данных TFRRS"],
       ["Иерархическая (многоуровневая) модель прогноза",
        "Общий для всех спортсменов «стартовый» прогноз, который уточняется собственными данными; решает холодный старт "
        "без жёстких правил [@gel, @spy]",
        "Обучение на открытых данных, перенос параметров в приложение"],
       ["Анализ видео прыжка на телефоне",
        "Длины скачка, шага и прыжка, скорость последних шагов разбега по позе тела [@bpose]; соотношение фаз — известный "
        "фактор результата в тройном [@hay]",
        "Модель позы на устройстве (MediaPipe/BlazePose), съёмка сбоку"],
       ["Языковая модель на устройстве: дообучение и контекст дневника",
        "Ответы на вопросы по дневнику, недельная сводка, разбор заметок (самочувствие, боль, сон); числа берутся из "
        "проверенных моделей, языковая модель их только объясняет",
        "Qwen2.5 0,5–1,5 млрд параметров (Apache 2.0) [@qwen, @apache] через llama.cpp [@llama]; LoRA-адаптер на "
        "собственных текстах; правовые условия — раздел 16. Основа уже есть: свой помощник (раздел 14) отвечает на "
        "типовые вопросы, языковая модель нужна только для свободных"],
       ["Федеративное обучение",
        "Модель дообучается на дневниках многих спортсменов без передачи самих дневников [@mcm]",
        "Сервер агрегации весов, согласие пользователей"],
       ["Понятный диапазон прогноза",
        "«Обычно от … до …» по естественному разбросу 2,5 % вместо «коридора»",
        "Квантили ошибок из данных TFRRS"],
       ["Поправка на ветер в дневнике",
        "Сравнимые результаты разных стартов (≈ 5 см на 1 м/с) [@wind]", "Ветер уже есть в погоде результата"]],
      [45, 75, 45])

# ================================================================== 15
h1("16 Правовые аспекты")
p("Исследование и внедрение ИИ в приложение должны соответствовать законодательству РФ и не нарушать авторских прав. "
  "Ниже — как это учтено. Раздел — разбор автора, а не юридическое заключение; спорные места согласуются с научным "
  "руководителем.")
h2("16.1 Авторские права и лицензии")
bullets([
    ("Тексты исследований. ", "Идеи, методы и факты авторским правом не охраняются, охраняется форма произведения "
     "(ГК РФ, ст. 1259) [@gk4]. Поэтому выводы литературы излагаются своими словами со ссылкой на источник; цитаты — "
     "только в объёме, оправданном научной и учебной целью, с указанием автора и источника (ст. 1274)."),
    ("Обучение языковой модели. ", "В обучающий набор не включаются тексты статей и книг; только собственные пересказы "
     "выводов, правила советов приложения и примеры тренера — с его письменного согласия на использование."),
    ("Выбор модели по лицензии. ", "Qwen2.5 размером 0,5–1,5 млрд параметров распространяется по лицензии Apache 2.0 "
     "[@qwen, @apache]: разрешены использование, дообучение и распространение, в том числе в коммерческом приложении, "
     "при сохранении текста лицензии и указании авторов. Версии Qwen2.5 на 3 и 72 млрд параметров имеют другую, более "
     "строгую лицензию и не используются. Запуск — через llama.cpp (лицензия MIT) [@llama], без зависимости от "
     "зарубежных облачных сервисов. Модели с лицензиями, содержащими собственные ограничения использования или "
     "зависящими от сервисов, доступность которых в РФ не гарантирована, не выбираются."),
    ("Библиотеки исследования. ", "scikit-learn, NumPy, python-docx — открытые лицензии (BSD, MIT); используются "
     "только в скриптах исследования, в приложение не входят."),
    ("Раздел «Лицензии» в приложении. ", "Перечень сторонних компонентов (модель, llama.cpp) с текстами лицензий."),
])
h2("16.2 Персональные данные")
bullets([
    ("Состав данных. ", "Данные дневника — персональные данные; заметки о боли и травмах относятся к сведениям о "
     "состоянии здоровья — специальной категории (152-ФЗ, ст. 10) [@pd]."),
    ("Обработка только на устройстве. ", "Все модели, включая языковую, работают на телефоне; данные не передаются "
     "третьим лицам и за рубеж. Поэтому не возникает трансграничной передачи (ст. 12) и обязанностей оператора, "
     "собирающего данные на сервере."),
    ("Облачные ИИ-сервисы не используются. ", "Их применение потребовало бы отдельного согласия на обработку "
     "сведений о здоровье и, для зарубежных сервисов, — оценки и уведомления о трансграничной передаче."),
    ("Федеративное обучение (раздел 15). ", "Остаётся перспективой: оно возможно только при явном согласии "
     "пользователей и без передачи самих дневников."),
])
h2("16.3 Не медицинское изделие")
p("Программное обеспечение, предназначенное для диагностики или лечения, может относиться к медицинским изделиям, "
  "подлежащим регистрации (323-ФЗ, ст. 38) [@med]. Приложение не ставит диагнозов: советы о резком спаде, нагрузке и "
  "жалобах в заметках сформулированы как тренировочные рекомендации («стоит снизить нагрузку, при боли — к врачу»), "
  "а ответы языковой модели помечаются: «ответ сгенерирован ИИ и не является медицинской рекомендацией».")
h2("16.4 Регулирование искусственного интеллекта")
p("Отдельного закона об ИИ в РФ пока нет. Ориентиры — Национальная стратегия развития ИИ [@u490], добровольный "
  "Кодекс этики в сфере ИИ [@ethic] и национальные стандарты доверия к системам ИИ [@gost]. Их основные принципы в "
  "проекте соблюдаются: прозрачность (у каждого вывода видно, на чём он основан и насколько надёжен), маркировка "
  "ответов ИИ, контроль человека (советы можно скрыть, решения принимают спортсмен и тренер), проверка моделей на "
  "реальных данных и честное указание ограничений.")
h2("16.5 Открытые данные, использованные в исследовании")
bullets([
    ("Что опубликовано. ", "В документ и репозиторий попадают только агрегированные результаты (средние ошибки, "
     "коэффициенты, доли); сырые данные не распространяются, спортсмены в расчётах обезличены."),
    ("Ограничения сайтов. ", "Сбор шёл с паузами между запросами; когда World Athletics ограничил доступ, ограничение "
     "не обходилось."),
    ("Требует проверки. ", "Лицензия набора GoldenCheetah OpenData и условия использования сайтов TFRRS и World "
     "Athletics в части автоматической загрузки."),
    ("Псевдонимизация в тестах. ", "Пять рядов TFRRS в тестах приложения подписаны метками A–E вместо имён, ссылки на "
     "профили убраны, номера стартовых профилей для сборщика в репозитории не хранятся. Это псевдонимизация, а не "
     "полное обезличивание: по датам и результатам открытых протоколов спортсмена теоретически можно найти, поэтому "
     "сами ряды не распространяются отдельно от тестов."),
])

# ================================================================== 16
h1("17 Ограничения")
bullets([
    "Прогноз подобран на студентах NCAA и спортсменах мирового уровня; юношей в данных мало (World Athletics ограничил "
    "доступ), тренировочных результатов в тройном нет вовсе.",
    "Связь «тренировка → результат» проверена на велосипедистах: перенос на прыжки — допущение. Открытого набора "
    "«упражнения + тройной по неделям» нет.",
    "Совпадение во времени не доказывает причину; на экране это сказано прямо.",
])

# ================================================================== литература
h1("Список литературы")
for i, (_, r) in enumerate(REFS, 1):
    par = doc.add_paragraph()
    par.paragraph_format.left_indent = Mm(10); par.paragraph_format.first_line_indent = Mm(-10)
    par.add_run(f"{i}. {r}")

# ================================================================== журнал
h1("Приложение А. Журнал изменений")
table("Журнал изменений исследований",
      ["Дата", "Что изменилось"],
      [["11.2026", "Советы на главном экране и в аналитике: правила по разбегу, целям, застою, нагрузке, подводке, "
        "пропускам, заметкам, весу, рекордам"],
       ["11.2026", "Время начала и окончания тренировки; модели «резкий спад» и «нагрузка по времени»"],
       ["11.2026", "Раздел «Советы и прогноз»: прогноз регрессией, связи со сдвигом; демо-дневник за год"],
       ["12.11.2026", "Прогноз: окно 180 дней, холодный старт по возрасту, прогноз по любому показателю; проверка на "
        "трёх рядах TFRRS"],
       ["12.11.2026", "Связи: перестановочный тест и поправка Бенджамини–Хохберга (ложные связи на шуме 42 % → 6 %)"],
       ["13.11.2026", "Создан единый документ; источник связи «присед на кг веса» заменён на Wagner и соавт., 2023"],
       ["13.11.2026", f"Собраны открытые данные: {N_TF} прыгунов TFRRS, {B['athletes']} профилей World Athletics, "
        f"{GC['athletes']} велосипедистов GoldenCheetah. Прогноз заменён на сглаживание с обученным весом "
        f"(ошибка {m(ALL['ses'])} против {m(ALL['app'])} м у прежней модели); проверены признаки из тренерской "
        f"литературы, правило прироста по возрасту, модель связей и модель «форма — усталость» на велосипедистах; "
        f"добавлены идеи развития ИИ"],
       ["13.11.2026", "Добавлены рекордные точки на графике; раздел «Правовые аспекты» (авторские права и лицензии, "
        "152-ФЗ, медицинские изделия, регулирование ИИ, открытые данные); языковая модель в идеях — Qwen2.5 (Apache 2.0) "
        "через llama.cpp"],
       ["13.11.2026", "Ряды TFRRS в тестах псевдонимизированы (метки A–E вместо имён, без ссылок на профили)"],
       ["13.11.2026", f"Свой помощник «Спросить дневник» (раздел 14): модели понимания вопросов и разбора заметок "
        f"(логистическая регрессия на буквенных n-граммах, обучена на собственных примерах). Вопросы: "
        f"{pc(AS['intents']['hard_accuracy'])} на трудном наборе против {pc(AS['intents']['keywords_hard_accuracy'])} у "
        f"ключевых слов; боль в заметках: F1 {m(AS['notes']['pain_model_hard_f1'], 2)} против "
        f"{m(AS['notes']['pain_regex_hard_f1'], 2)}. Совет о жалобах учитывает модель"]],
      [30, 135])

footer = sec.footer.paragraphs[0]; footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = footer.add_run()
for tag, text in (("begin", None), (None, "PAGE"), ("end", None)):
    if tag:
        el = OxmlElement("w:fldChar"); el.set(qn("w:fldCharType"), tag)
    else:
        el = OxmlElement("w:instrText"); el.set(qn("xml:space"), "preserve"); el.text = text
    run._r.append(el)

doc.save(OUT)
print("saved", OUT)
