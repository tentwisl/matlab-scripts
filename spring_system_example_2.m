nodeCount = 4;

conn = [ 1 2;    % k1
         2 3;    % k2 #1
         2 3;    % k2 #2
         3 4;    % k3
         2 4 ];  % k4

k_e = [300; 250; 250; 500; 700]; %stiffness

fixedNodes = [1 4]; %supports

loads = zeros(nodeCount,1); %nodal loads
loads(2) = 20;

[u, fElem, reactions, Kglob, Fglob] = direct_solver_tommy(nodeCount, conn, k_e, fixedNodes, loads);
u_mm = u * 1000; %converting to mm 
disp('displacements u (mm):'); disp(u_mm)
disp('element forces (N, +tension i->j:'); disp(fElem)
disp('reactions at supports (N):'); disp(reactions([1 4]))
disp('global stiffness K (N/m):'); disp(Kglob)
