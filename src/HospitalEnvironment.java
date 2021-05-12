import jason.asSyntax.Literal;
import jason.asSyntax.Structure;
import jason.environment.Environment;
import jason.environment.grid.Location;

import javax.swing.*;
import java.util.*;


public class HospitalEnvironment extends Environment {
    /* Agent BELIEFS as literals */
    // Manager
    public static final Literal newPatient = Literal.parseLiteral("newPatient(action_id,patient_id,loc_to)");


    /// Carrier
    // belief
    public static final Literal position = Literal.parseLiteral("pos(a,x,y)");
    public static final Literal distance = Literal.parseLiteral("dist(a,d)");
    // action
    public static final Literal move_towards = Literal.parseLiteral("move_towards(x,y)");
    public static final Literal pick = Literal.parseLiteral("pick");
    public static final Literal drop = Literal.parseLiteral("drop");


    private HashMap<Department, Location> departments;
    private HashMap<Department, ArrayList<Location>> routesFromReception;
    private HashMap<Carrier, ArrayList<Location>> carrierTask;
    private Location receptionPosition;
    private ArrayList<Carrier> carrierAgents;
    private Manager managerAgent;
    private Reception reception;

    private HospitalModel hospitalModel;


    HospitalView hospitalView;

    @Override
    public void init(String[] args) {
	int hospitalSize = 24;
	int numOfCarriers = 1;
        hospitalModel = new HospitalModel(hospitalSize, numOfCarriers);
        // placing the front door
        reception = new Reception(20, this);
        receptionPosition = new Location(hospitalSize / 2, 0);
        hospitalModel.placeReception(receptionPosition);

        hospitalModel.placeWalls(4);

        // placing the departments randomly
        departments = new HashMap<>();
        routesFromReception = new HashMap<>();
        int depID = 0;
        for(SicknessType depType : SicknessType.values()){
            Department department = new Department(depType);
            Location depPos = hospitalModel.placeDepartment(depID++);
            addPercept(Literal.parseLiteral("pos(\"" + depType.name() +"\","+ depPos.x + "," + depPos.y +")"));
            departments.put(department, depPos);
            routesFromReception.put(department, hospitalModel.findShortestPathFromReception(depPos));
        }

        // initializing Agents
        managerAgent = new Manager(0);
        carrierAgents = new ArrayList<>();
        carrierTask = new HashMap<>();
        for(int i=0; i<numOfCarriers; i++){
            int x = (hospitalSize - numOfCarriers) / 2;
            hospitalModel.placeAgent(i);
            Location carrierLoc = new Location(x+i, 5);
            carrierAgents.add(new Carrier(i, carrierLoc, this));
            addPercept(Literal.parseLiteral("pos(r"+ i +","+ carrierLoc.x + "," + carrierLoc.y +")"));
        }


        hospitalView = new HospitalView(hospitalModel, this);
        //hospitalView.setEnv(this);

        addPercept(Literal.parseLiteral("pos(\"Reception\","+ receptionPosition.x + "," + receptionPosition.y +")"));
    }

    @Override
    public boolean executeAction(String agName, Structure act) {
        boolean result = false;
        System.out.println(agName +" doing: "+ act);
        Carrier c = carrierAgents.get(Integer.parseInt(agName.substring(1))-1);


        //az agent hív egy move_towards(X,Y)-t, ekkor az env-nek egyet kell léptetni a jó irányba
        if(act.toString().contains("move_towards")) {
            // retrieving the selected carrier
            // getting its destination which can be {reception, department}
            int x = Integer.parseInt(act.toString().substring(act.toString().indexOf('(')+1, act.toString().indexOf(',')));
            int y = Integer.parseInt(act.toString().substring(act.toString().indexOf(',')+1, act.toString().indexOf(')')));
            Location destination = new Location(x,y);
            /// debug
            System.out.println("Agents's destination: { " + x + ", " + y + " }");
            System.out.println("Agents's position: { " + c.currentPosition.x + ", " + c.currentPosition.y + " }");
            removePercept(Literal.parseLiteral("pos(r"+ c.id +","+ c.currentPosition.x + "," + c.currentPosition.y +")"));

            Location step = null;
            // if we have already assigned a task for the carrier we should move it to the next step
            if (carrierTask.containsKey(c)) {
                if(carrierTask.get(c).size() == 0) carrierTask.remove(c);
                else step = carrierTask.get(c).remove(0);
            }
            // if not then, calculate the route, and assign the task, and pop the first element
            else {
                /// check whether the destination is the reception or it is a department
                // we need to go to the reception first
                if(destination.equals(receptionPosition)){
                    ArrayList<Location> route = hospitalModel.findShortestPathToReception(c.currentPosition);
                    carrierTask.put(c, route);
                    // popping the first pos, which is the agent's initial pos
                    carrierTask.get(c).remove(0);
                    step = carrierTask.get(c).remove(0);
                }
                // we are heading to the department
                else {
                    // check which department is the destination
                    Department d = null;
                    for (Map.Entry<Department, Location> e : departments.entrySet()){
                        if(e.getValue().equals(destination))
                            d = e.getKey();
                    }

                    carrierTask.put(c, routesFromReception.get(d));
                    // popping the first pos, which is the agent's initial pos
                    carrierTask.get(c).remove(0);
                    step = carrierTask.get(c).remove(0);
                }
            }
                System.out.println("Next step leads to: { " + step.x + ", " + step.y + " }");
                hospitalModel.moveAgent(c, step);
                addPercept(Literal.parseLiteral("pos(r"+ c.id +","+ c.currentPosition.x + "," + c.currentPosition.y +")"));
                System.out.println(Literal.parseLiteral("pos(r"+ c.id +","+ c.currentPosition.x + "," + c.currentPosition.y +")"));
                result = true;

        }
        else if(act.toString().contains("arrived")){
            carrierTask.remove(c);
            result = true;
        }
        else if(act.toString().contains("pickup")){
            c.takePatient();
            result = true;

        }
        else if(act.toString().contains("drop")){
            c.dropPatient();
            result = true;

        }

        hospitalView.repaint();


        if (result) {
            updateBelief();
            try {
                Thread.sleep(200);
            } catch (Exception ignored) {
            }
        }

        return result;
    }

    private void updateBelief(){
        //clearAllPercepts();



    }

    public HashMap<Department, Location> getDepartments(){
        return departments;
    }

    public ArrayList<Carrier> getCarrierAgents(){
        return carrierAgents;
    }

    public void advertisePatient(Patient p ) {
        if(p != null) {
            addPercept("testManager", Literal.parseLiteral("newPatient(" + p.getId() + "," + p.getId() + ",\"" + p.getType() + "\")"));
        }
    }

    public void addPatient(SicknessType type){
        Random r = new Random();
        int age = 10 + r.nextInt(90);
        Patient p = new Patient(age, type);
        reception.placePatient(p);
    }

    public Location getReceptionPosition(){
        return receptionPosition;
    }

    public HospitalElement getHEfromPosition(Location loc){
        if (loc.equals(receptionPosition)){
            return reception;
        }
        Department d = null;
        for (Map.Entry<Department, Location> e : departments.entrySet()){
            if(e.getValue().equals(loc))
                return e.getKey();
            }
        return null;
    }
}
