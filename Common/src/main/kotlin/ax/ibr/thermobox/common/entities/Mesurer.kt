package ax.ibr.thermobox.common.entities

import java.sql.Date

class Mesurer : Temperature {
    constructor(value: Float, date: Date) : super(value, date)
}