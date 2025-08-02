package inu.timetable.enums;

public enum ClassMethod {
    ONLINE("온라인"),
    OFFLINE("오프라인"), 
    BLENDED("온오프라인");
    
    private final String description;
    
    ClassMethod(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}