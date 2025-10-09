nodeCount = 4;              %numnodes
conn = [1 2; 2 3; 3 4];     %nodes [i, j]       
k_e  = [200; 150; 120];     %N/mm
fixedNodes = [1];           %just one at wall 
loads = [0; 5000; 0; -3000]

[u1, fElem1, R1, K1, F1] = direct_solver_tommy(nodeCount, conn, k_e, fixedNodes, loads); %uses general solver
disp('P1: nodal displacements (mm)'); disp(u1);
disp('P1: element forces (kN, +tension i->j)'); disp(fElem1);
disp('P1: reactions (kN)'); disp(R1);
