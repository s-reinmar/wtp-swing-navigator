package pl.j.reinmar.mapwaw.wtp.model;

/**
 * Klasa domenowa reprezentująca linię komunikacyjną (np. 507, 17, M1).
 */
public class Line implements java.io.Serializable {
    private String lineNumber;          // Numer linii (np. 507, 17, M1)
    private TransportType transportType; // Typ transportu: BUS, TRAM, METRO
    private String operator;            // Przewoźnik (np. MZA, TW)

    public Line(String lineNumber, TransportType transportType, String operator) {
        this.lineNumber = lineNumber;
        this.transportType = transportType;
        this.operator = operator;
    }

    public String getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(String lineNumber) {
        this.lineNumber = lineNumber;
    }

    public TransportType getTransportType() {
        return transportType;
    }

    public void setTransportType(TransportType transportType) {
        this.transportType = transportType;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    @Override
    public String toString() {
        return "Line{" +
                "lineNumber='" + lineNumber + '\'' +
                ", transportType=" + transportType +
                ", operator='" + operator + '\'' +
                '}';
    }
}